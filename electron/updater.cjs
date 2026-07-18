// GitHub Release 更新檢查
const https = require("https");
const semver = require("semver");

// TODO: 修改為您自己的 GitHub repo
const REPO = "Nimiaoder/PoeFiveWayScript";

function fetchLatest() {
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        host: "api.github.com",
        path: `/repos/${REPO}/releases/latest`,
        method: "GET",
        headers: { "User-Agent": "poe-five-army-script" },
        timeout: 8000,
      },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => {
          try { resolve(JSON.parse(data)); }
          catch (e) { reject(e); }
        });
      }
    );
    req.on("error", reject);
    req.on("timeout", () => req.destroy(new Error("timeout")));
    req.end();
  });
}

// 檢查更新：回傳 { hasUpdate, latest, current, url }
async function checkForUpdates(currentVersion) {
  try {
    const data = await fetchLatest();
    const latest = (data.tag_name || "").replace(/^v/, "");
    if (!latest) return { ok: false, error: "無法取得版本資訊" };
    const hasUpdate = semver.valid(latest) && semver.gt(latest, currentVersion);
    return {
      ok: true,
      hasUpdate,
      latest,
      current: currentVersion,
      url: data.html_url,
    };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

module.exports = { checkForUpdates };
