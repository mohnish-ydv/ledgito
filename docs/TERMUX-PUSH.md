# Exact Termux Push — Ledgito 1.2 Professional Experience

These commands assume the downloaded ZIP is in Android `Download` and the existing Git repository folder is `~/storage/downloads/ledgerly`.

```bash
cd ~/storage/downloads

rm -rf Ledgito-v1.2.0
mkdir Ledgito-v1.2.0

unzip -o Ledgito-v1.2.0-Professional-Experience-GitHub-Ready.zip \
  -d Ledgito-v1.2.0

cp -af \
  Ledgito-v1.2.0/Ledgito-v1.2.0-Professional-Experience-GitHub-Ready/. \
  ledgerly/

cd ~/storage/downloads/ledgerly
python3 tools/validate_project.py
python3 tools/test_schema.py

git status
git add -A
git commit -m "Ledgito v1.2 Professional Experience"
git push origin main
```

GitHub Actions workflow: **Build Ledgito 1.2 Professional Experience APK**

Artifact: `Ledgito-v1.2.0-Professional-Experience-APK`

APK: `Ledgito-v1.2.0-Professional-Experience.apk`

If Termux reports that its current directory no longer exists, first run:

```bash
cd ~
```

Then run the commands above from the beginning.
