#!/bin/bash
# Aircraft Data Analyzer — FAA Corpus Downloader
# Run this on your Mac to download all handbooks and ADs
# Usage: chmod +x download_ada_corpus.sh && ./download_ada_corpus.sh

set -e

DIR="$HOME/Projects/boeing/maintenance"
AD_DIR="$DIR/airworthiness_directives"

mkdir -p "$DIR"
mkdir -p "$AD_DIR"

echo "============================================================"
echo "Aircraft Data Analyzer — FAA Corpus Downloader"
echo "Output: $DIR"
echo "============================================================"

# ── HANDBOOKS ────────────────────────────────────────────────────
echo ""
echo "Downloading FAA AMT Handbooks..."

download() {
  local url=$1
  local dest=$2
  if [ -f "$dest" ]; then
    echo "  [skip] $(basename $dest) already exists"
  else
    echo "  Downloading $(basename $dest)..."
    curl -L --progress-bar -o "$dest" "$url"
    echo "  ✓ $(basename $dest) ($(du -sh "$dest" | cut -f1))"
  fi
}

download \
  "https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/amt_general_handbook.pdf" \
  "$DIR/FAA-H-8083-30B_General.pdf"

download \
  "https://www.faa.gov/regulations_policies/handbooks_manuals/aviation/FAA-H-8083-31B_Aviation_Maintenance_Technician_Handbook.pdf" \
  "$DIR/FAA-H-8083-31B_Airframe.pdf"

download \
  "https://www.faa.gov/regulations_policies/handbooks_manuals/aviation/amt_powerplant_handbook.pdf" \
  "$DIR/FAA-H-8083-32B_Powerplant.pdf"

download \
  "https://www.faa.gov/documentlibrary/media/advisory_circular/ac_43.13-1b_w-chg1.pdf" \
  "$DIR/AC_43-13-1B_Inspection_Repair.pdf"

echo ""
echo "All handbooks downloaded."

# ── AIRWORTHINESS DIRECTIVES ────────────────────────────────────
echo ""
echo "Downloading Airworthiness Directives..."
echo "(Requires Python 3 — checking...)"
python3 --version || { echo "Python 3 not found. Install it and re-run."; exit 1; }

python3 - "$AD_DIR" << 'PYEOF'
import sys, requests, json, time, os, re
from datetime import datetime

AD_DIR = sys.argv[1]
BASE_URL = "https://www.federalregister.gov/api/v1/documents.json"

SYSTEMS = [
    ("airworthiness directive integrated drive generator IDG",      "ATA-24", "Electrical_Power",      10),
    ("airworthiness directive auxiliary power unit APU turbine",    "ATA-49", "APU",                   10),
    ("airworthiness directive landing gear inspection retraction",  "ATA-32", "Landing_Gear",           10),
    ("airworthiness directive fuel tank wiring ignition source",    "ATA-28", "Fuel_System",            10),
    ("airworthiness directive hydraulic actuator flight control",   "ATA-29", "Hydraulics",             10),
    ("airworthiness directive pressurization outflow valve cabin",  "ATA-21", "Air_Conditioning_Press", 10),
    ("airworthiness directive door latch emergency exit",           "ATA-52", "Doors",                  10),
    ("airworthiness directive fire detection loop extinguishing",   "ATA-26", "Fire_Protection",        10),
    ("airworthiness directive oxygen system passenger supply mask", "ATA-35", "Oxygen",                  8),
    ("airworthiness directive flight control elevator rudder",      "ATA-27", "Flight_Controls",        10),
    ("airworthiness directive engine fan blade compressor",         "ATA-72", "Engine",                 10),
    ("airworthiness directive avionics flight management computer", "ATA-34", "Navigation",              8),
]

def sanitize(text):
    return re.sub(r'[^a-zA-Z0-9_\-]', '_', text)[:60]

all_meta, seen, total = [], set(), 0

for term, ata, name, n in SYSTEMS:
    print(f"\n  [{ata}] {name}")
    try:
        r = requests.get(BASE_URL, params={
            "conditions[agencies][]": "federal-aviation-administration",
            "conditions[term]": term,
            "per_page": n, "order": "newest",
            "fields[]": ["title","abstract","document_number",
                         "publication_date","effective_on","html_url","pdf_url","type"],
        }, timeout=15)
        docs = [d for d in r.json().get("results",[]) if d.get("type")=="Rule"]
    except Exception as e:
        print(f"    ERROR: {e}"); continue

    sdir = os.path.join(AD_DIR, f"{ata}_{name}")
    os.makedirs(sdir, exist_ok=True)

    for doc in docs:
        num = doc.get("document_number","UNKNOWN")
        if num in seen: continue
        seen.add(num)
        url = doc.get("pdf_url")
        if not url: continue
        date = doc.get("publication_date","0000-00-00")
        fpath = os.path.join(sdir, f"{date}_{num}_{sanitize(doc.get('title',''))}.pdf")
        if os.path.exists(fpath):
            print(f"    [skip] {num}"); continue
        try:
            resp = requests.get(url, timeout=30, stream=True)
            resp.raise_for_status()
            with open(fpath,'wb') as f:
                for chunk in resp.iter_content(8192): f.write(chunk)
            kb = os.path.getsize(fpath)//1024
            print(f"    ✓  {num}  ({date})  {kb} KB")
            total += 1
            all_meta.append({"document_number":num,"title":doc.get("title"),
                "abstract":doc.get("abstract"),"publication_date":date,
                "effective_on":doc.get("effective_on"),"ata_chapter":ata,
                "system":name,"html_url":doc.get("html_url"),"pdf_url":url,
                "local_path":os.path.relpath(fpath,AD_DIR)})
        except: print(f"    ✗  {num}  failed")
        time.sleep(0.25)

index = os.path.join(AD_DIR,"ad_index.json")
with open(index,"w") as f:
    json.dump({"generated":datetime.now().isoformat(),
        "total_ads":len(all_meta),
        "systems_covered":{s[1]:s[2] for s in SYSTEMS},
        "ads":sorted(all_meta,key=lambda x:x["publication_date"],reverse=True)},f,indent=2)

print(f"\n  ✓  {total} ADs downloaded")
print(f"  ✓  Index: {index}")
PYEOF

echo ""
echo "============================================================"
echo "DONE — corpus saved to: $DIR"
echo "  Handbooks : 4 PDFs"
echo "  ADs       : organized by ATA chapter in airworthiness_directives/"
echo "============================================================"
