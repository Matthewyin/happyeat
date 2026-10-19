import * as esbuild from "esbuild";
import fs from "node:fs";
import path from "node:path";
const root = path.dirname(new URL(import.meta.url).pathname);
const dist = path.join(root, "dist");
fs.rmSync(dist, { recursive: true, force: true });
fs.mkdirSync(dist, { recursive: true });
await esbuild.build({ entryPoints: [path.join(root, "src/main.ts")], bundle: true, outfile: path.join(dist, "game.js"), format: "iife", target: ["chrome70", "ios12"], minify: false, logLevel: "info" });
for (const d of ["assets", "openDataContext"]) {
  fs.cpSync(path.join(root, d), path.join(dist, d), { recursive: true });
}
fs.copyFileSync(path.join(root, "game.json"), path.join(dist, "game.json"));
fs.copyFileSync(path.join(root, "harness.html"), path.join(dist, "harness.html"));
console.log("build ok → dist/");
