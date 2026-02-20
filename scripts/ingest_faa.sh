#!/bin/bash
# scripts/ingest_faa.sh
# Uploads FAA handbook PDFs to the ingestion endpoint.
# Uses pre-split files from faa_handbooks_split/ to stay under Cloudflare's
# body size limit. The server processes uploads asynchronously, so a delay
# between uploads prevents concurrent large-PDF parsing from exhausting memory.

APP_URL="${1:-https://ada-document-search.apps.tas-ndc.kuhn-labs.com}"
CORPUS_DIR="${2:-/Users/corby/Projects/boeing/maintenance}"
DELAY_BETWEEN=5

echo "Waiting for app to be healthy..."
until curl -sf "$APP_URL/actuator/health" > /dev/null; do
  sleep 5
done

echo "=== Ingesting FAA Handbooks (split files) ==="
for pdf in "$CORPUS_DIR"/faa_handbooks_split/*.pdf; do
  filename=$(basename "$pdf")
  echo "  Uploading $filename..."
  curl -X POST "$APP_URL/admin/ingest/upload" \
    -F "file=@$pdf" \
    -F "docType=handbook" \
    -F "source=real"
  echo ""
  echo "  Waiting ${DELAY_BETWEEN}s before next upload..."
  sleep "$DELAY_BETWEEN"
done

echo "=== All uploads submitted ==="
echo "Monitor ingestion progress with: cf logs ada-document-search"
