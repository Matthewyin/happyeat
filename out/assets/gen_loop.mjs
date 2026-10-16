const fs = await import("node:fs/promises");

const task = await taskSpace(8);
const page = task.page("p1");

const batch = JSON.parse(await fs.readFile("/Users/matthewyin/Coding/happyeat/out/assets/batch.json", "utf8"));
const rawDir = "/Users/matthewyin/Coding/happyeat/out/sprites_raw";
await fs.mkdir(rawDir, { recursive: true });

function listImageUrls() {
  return page.evaluate(function () {
    return Array.prototype.map.call(document.querySelectorAll("img"), function (i) { return i.src; })
      .filter(function (s) { return s.indexOf("http") === 0 && s.indexOf("r2-bucket") > 0; });
  });
}

async function download(url, outPath) {
  await page.waitForTimeout(3000);
  await page.fetch(url, { saveAs: outPath, timeout: 60000 });
}

const results = [];
const known = new Set(await listImageUrls());
console.log("known urls:", known.size);

const playerPath = rawDir + "/player.png";
if (known.size > 0 && !(await fs.stat(playerPath).catch(function () { return null; }))) {
  const url = Array.from(known)[0];
  await download(url, playerPath);
  const size = (await fs.stat(playerPath)).size;
  console.log("[player] recovered " + Math.round(size / 1024) + "KB from " + url.slice(0, 80));
}

for (const item of batch.tasks) {
  if (item.id === "player") continue;
  const outPath = rawDir + "/" + item.id + ".png";
  let status = "";
  try {
    const existing = await fs.stat(outPath).catch(function () { return null; });
    if (existing && existing.size > 100000) {
      status = "already downloaded";
    } else {
      await page.click('loc=role:textbox[name*="念头"]', { label: "聚焦输入框" });
      await page.waitForTimeout(700);
      if (item.id === "background") {
        const labels = await page.evaluate(function () {
          return Array.prototype.map.call(document.querySelectorAll("button"), function (b) {
            return b.getAttribute("aria-label") || "";
          }).filter(function (a) { return a.indexOf("Aspect") === 0; });
        });
        console.log("aspect buttons now:", JSON.stringify(labels));
        await page.click('loc=css:button[aria-label^="Aspect"]', { label: "打开比例菜单" });
        await page.waitForTimeout(700);
        await page.click('text="9:16"', { label: "比例选 9:16" });
        await page.waitForTimeout(600);
      }
      try {
        await page.fill('loc=role:textbox[name*="念头"]', item.prompt);
      } catch (eFill) {
        await page.click('loc=role:textbox[name*="念头"]', { label: "聚焦输入框(重试)" });
        await page.keyboard.paste(item.prompt);
      }
      await page.waitForTimeout(400);
      try {
        await page.click('loc=role:button[name="Send"]', { label: "发送 " + item.id });
      } catch (eSend) {
        await page.click('loc=role:button[name="发送"]', { label: "发送 " + item.id });
      }
      console.log("sent:", item.id, new Date().toISOString());
      const deadline = Date.now() + 300000;
      let fresh = null;
      while (Date.now() < deadline) {
        await page.waitForTimeout(12000);
        const urls = await listImageUrls();
        fresh = urls.find(function (u) { return !known.has(u); });
        if (fresh) break;
      }
      if (!fresh) throw new Error("timeout waiting for image");
      known.add(fresh);
      await download(fresh, outPath);
      const size = (await fs.stat(outPath)).size;
      if (size < 100000) throw new Error("suspicious small file " + size);
      status = "ok " + Math.round(size / 1024) + "KB";
    }
  } catch (e) {
    status = "FAILED: " + String(e).slice(0, 200);
  }
  console.log("[" + item.id + "] " + status);
  results.push(item.id + ": " + status);
}

console.log("=== SUMMARY ===");
console.log(results.join("\n"));
