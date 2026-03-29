const puppeteer = require("/tmp/mupo-shot/node_modules/puppeteer-core");

async function main() {
  const browser = await puppeteer.launch({
    headless: "new",
    executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    args: ["--no-sandbox"],
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 2048, height: 1167, deviceScaleFactor: 1 });

  const baseUrl = "http://localhost:5173";
  const outputDir =
    "/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/docs/screenshots";

  const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await pause(1500);
  await page.screenshot({ path: `${outputDir}/login-page.png`, fullPage: true });

  await page.type('input[placeholder="Enter your username"]', "admin");
  await page.type('input[placeholder="Enter your password"]', "Admin@123");
  await page.click('button[type="submit"]');
  await pause(4000);
  console.log("Current URL after sign-in:", page.url());

  await page.screenshot({ path: `${outputDir}/order-review-page.png`, fullPage: true });

  await page.goto(`${baseUrl}/products`, { waitUntil: "domcontentloaded" });
  await pause(1500);
  await page.screenshot({ path: `${outputDir}/products-page.png`, fullPage: true });

  await page.goto(`${baseUrl}/admin/products`, { waitUntil: "domcontentloaded" });
  await pause(1500);
  await page.screenshot({ path: `${outputDir}/product-admin-page.png`, fullPage: true });

  await browser.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
