import http from "k6/http";
import exec from "k6/execution";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const baseUrl = __ENV.BASE_URL || "http://localhost:8000";
const username = __ENV.USERNAME || "john_smith";
const password = __ENV.PASSWORD || "User@123";
const adminUsername = __ENV.ADMIN_USERNAME || "admin";
const adminPassword = __ENV.ADMIN_PASSWORD || "Admin@123";
const productIdEnv = __ENV.PRODUCT_ID ? Number(__ENV.PRODUCT_ID) : null;
const testCase = __ENV.TEST_CASE || "unique_orders";
const pauseSeconds = Number(__ENV.SLEEP_SECONDS || "0.1");

const endpointCreateLatency = new Trend("b72_create_latency_ms", true);
const endpointPayLatency = new Trend("b72_pay_latency_ms", true);
const endpointApproveLatency = new Trend("b72_approve_latency_ms", true);
const endpointRejectLatency = new Trend("b72_reject_latency_ms", true);
const endpointMyOrdersLatency = new Trend("b72_my_orders_latency_ms", true);
const endpointAdminOrdersLatency = new Trend("b72_admin_orders_latency_ms", true);

const endpointCreateErrors = new Counter("b72_create_errors_total");
const endpointPayErrors = new Counter("b72_pay_errors_total");
const endpointApproveErrors = new Counter("b72_approve_errors_total");
const endpointRejectErrors = new Counter("b72_reject_errors_total");

const http409Total = new Counter("b72_http_409_total");
const http429Total = new Counter("b72_http_429_total");
const http504Total = new Counter("b72_http_504_total");
const http5xxTotal = new Counter("b72_http_5xx_total");
const unexpected5xxRate = new Rate("b72_unexpected_5xx_rate");

const stageVus = Number(__ENV.VUS || "500");
const warmupDuration = __ENV.WARMUP || "20s";
const steadyDuration = __ENV.STEADY || "40s";
const cooldownDuration = __ENV.COOLDOWN || "10s";

export const options = {
  scenarios: {
    main: {
      executor: "ramping-vus",
      startVUs: 1,
      stages: [
        { duration: warmupDuration, target: stageVus },
        { duration: steadyDuration, target: stageVus },
        { duration: cooldownDuration, target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ["rate<0.30"],
    http_req_duration: ["p(95)<3000"],
    b72_unexpected_5xx_rate: ["rate<0.05"],
    b72_create_latency_ms: ["p(95)<2000"],
    b72_pay_latency_ms: ["p(95)<2500"]
  }
};

function classifyStatus(status) {
  if (status === 409) http409Total.add(1);
  if (status === 429) http429Total.add(1);
  if (status === 504) http504Total.add(1);
  if (status >= 500) {
    http5xxTotal.add(1);
    unexpected5xxRate.add(1);
    return;
  }
  unexpected5xxRate.add(0);
}

function login(loginUsername, loginPassword) {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ username: loginUsername, password: loginPassword }),
    { headers: { "Content-Type": "application/json" } }
  );

  check(response, {
    "login status is 200": (r) => r.status === 200
  });

  const payload = JSON.parse(response.body || "{}");
  return payload?.data?.access_token;
}

function getProductId() {
  if (productIdEnv) return productIdEnv;

  const response = http.get(`${baseUrl}/api/products?page=1&size=3`);
  check(response, {
    "list products status is 200": (r) => r.status === 200
  });

  const payload = JSON.parse(response.body || "{}");
  const item = payload?.data?.items?.[0];
  return item?.id || 1;
}

function createOrder(userToken, productId, requestNoOverride) {
  const vu = typeof __VU === "undefined" ? "setup" : String(__VU);
  const iter = typeof __ITER === "undefined" ? "setup" : String(__ITER);
  const requestNo = requestNoOverride || `B72-${vu}-${iter}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/api/orders`,
    JSON.stringify({
      request_no: requestNo,
      product_id: productId,
      quantity: 1
    }),
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userToken}`
      }
    }
  );

  endpointCreateLatency.add(response.timings.duration);
  classifyStatus(response.status);
  if (response.status >= 400) endpointCreateErrors.add(1);

  check(response, {
    "create order status is 200": (r) => r.status === 200
  });

  const payload = JSON.parse(response.body || "{}");
  return payload?.data;
}

function payOrder(userToken, orderId) {
  const response = http.post(
    `${baseUrl}/api/orders/${orderId}/pay`,
    null,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userToken}`
      }
    }
  );

  endpointPayLatency.add(response.timings.duration);
  classifyStatus(response.status);
  if (response.status >= 400) endpointPayErrors.add(1);

  return response;
}

function approveOrder(adminToken, orderId) {
  const response = http.post(
    `${baseUrl}/api/admin/orders/${orderId}/approve`,
    null,
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${adminToken}`
      }
    }
  );

  endpointApproveLatency.add(response.timings.duration);
  classifyStatus(response.status);
  if (response.status >= 400) endpointApproveErrors.add(1);
  return response;
}

function rejectOrder(adminToken, orderId) {
  const response = http.post(
    `${baseUrl}/api/admin/orders/${orderId}/reject`,
    JSON.stringify({ reject_reason: "Batch 7.2 concurrency validation" }),
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${adminToken}`
      }
    }
  );

  endpointRejectLatency.add(response.timings.duration);
  classifyStatus(response.status);
  if (response.status >= 400) endpointRejectErrors.add(1);
  return response;
}

function sampleReadEndpoints(userToken, adminToken) {
  const myOrders = http.get(`${baseUrl}/api/orders/my?page=1&size=10`, {
    headers: { Authorization: `Bearer ${userToken}` }
  });
  endpointMyOrdersLatency.add(myOrders.timings.duration);
  classifyStatus(myOrders.status);

  const adminOrders = http.get(`${baseUrl}/api/admin/orders?page=1&size=10`, {
    headers: { Authorization: `Bearer ${adminToken}` }
  });
  endpointAdminOrdersLatency.add(adminOrders.timings.duration);
  classifyStatus(adminOrders.status);
}

export function setup() {
  const userToken = login(username, password);
  const adminToken = login(adminUsername, adminPassword);
  const productId = getProductId();

  const setupData = {
    userToken,
    adminToken,
    productId
  };

  if (testCase === "same_order_retry") {
    const order = createOrder(userToken, productId, `B72-setup-${Date.now()}`);
    setupData.sameOrderId = order?.id;
  }

  return setupData;
}

export default function (data) {
  if (testCase === "same_order_retry") {
    if (!data.sameOrderId) return;
    payOrder(data.userToken, data.sameOrderId);
    if (exec.scenario.iterationInTest % 5 === 0) {
      sampleReadEndpoints(data.userToken, data.adminToken);
    }
    sleep(pauseSeconds);
    return;
  }

  const order = createOrder(data.userToken, data.productId);
  const orderId = order?.id;

  if (!orderId) {
    sleep(pauseSeconds);
    return;
  }

  const payResponse = payOrder(data.userToken, orderId);
  const isPaySuccess = payResponse.status === 200;

  if (isPaySuccess) {
    const shouldApprove = (exec.scenario.iterationInTest + __VU) % 2 === 0;
    if (shouldApprove) {
      approveOrder(data.adminToken, orderId);
    } else {
      rejectOrder(data.adminToken, orderId);
    }
  }

  if (exec.scenario.iterationInTest % 3 === 0) {
    sampleReadEndpoints(data.userToken, data.adminToken);
  }

  sleep(pauseSeconds);
}
