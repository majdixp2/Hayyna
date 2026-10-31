import { readFileSync, existsSync } from "node:fs";
if (!existsSync("eslint.json")) { console.log("::warning::eslint produced no report"); process.exit(0); }
const res = JSON.parse(readFileSync("eslint.json", "utf8"));
let e = 0, w = 0;
for (const f of res) for (const m of f.messages) {
  const file = "web/" + f.filePath.split("/web/").pop();
  if (m.severity === 2) { e++; console.log(`::error file=${file},line=${m.line},title=${m.ruleId}::${m.message}`); } else w++;
}
console.log(`::notice title=eslint::${e} errors, ${w} warnings`);
