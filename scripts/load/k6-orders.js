import http from "k6/http";
import { check, sleep } from "k6";

const vus = Number(__ENV.VUS || "1");
const duration = __ENV.DURATION || "20s";
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || "2");

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1500"]
  }
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8000";
const username = __ENV.USERNAME || "john_smith";
const password = __ENV.PASSWORD || "User@123";
const productId = Number(__ENV.PRODUCT_ID || "1");

function login() {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: {
        "Content-Type": "application/json"
      }
    }
  );

  check(response, {
    "login status is 200": (r) => r.status === 200
  });

  const payload = JSON.parse(response.body);
  return payload?.data?.access_token;
}

export function setup() {
  return {
    token: login()
  };
}

export default function (data) {
  const requestNo = `K6-${__VU}-${__ITER}-${Date.now()}`;

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
        Authorization: `Bearer ${data.token}`
      }
    }
  );

  check(response, {
    "create order status is 200": (r) => r.status === 200 || r.status === 201,
    "create order response has data": (r) => r.body.includes('"data"')
  });

  sleep(sleepSeconds);
}
