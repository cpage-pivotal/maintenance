#!/bin/bash
# scripts/ingest-upload.sh
# Uploads all corpus documents to the ingestion endpoint

APP_URL="${1:-https://ada-document-search.apps.tas-ndc.kuhn-labs.com}"
CORPUS_DIR="${2:-/Users/corby/Projects/boeing/maintenance}"

echo "Waiting for app to be healthy..."
until curl -sf "$APP_URL/actuator/health" > /dev/null; do
  sleep 5
done

# Airworthiness Directives (organized by ATA chapter)
echo "=== Ingesting Airworthiness Directives ==="
for pdf in "$CORPUS_DIR"/airworthiness_directives/ATA-*/*.pdf; do
  filename=$(basename "$pdf")
  ata_dir=$(basename "$(dirname "$pdf")")
  ata_chapter=$(echo "$ata_dir" | sed 's/ATA-\([0-9]*\).*/\1/')
  echo "  Uploading $filename (ATA $ata_chapter)..."
  curl -X POST "$APP_URL/admin/ingest/upload" \
    -F "file=@$pdf" \
    -F "docType=ad" \
    -F "ataChapter=$ata_chapter" \
    -F "source=real"
  echo ""
done

echo "=== Ingestion complete ==="