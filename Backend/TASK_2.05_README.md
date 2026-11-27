# Task 2.05 — Core class tests
Merge src/test into the existing Task 2.03 backend repository. Do not duplicate model classes. No production source changes are needed. ValidationTest.java is the existing suite, unchanged; CoreClassTest.java adds 17 named class behavior tests and 8 invalid-input/permission tests. Abstract UserAccount is tested through RegisteredGuest. No JUnit dependency is required.

From the backend project root in PowerShell (JDK 11 or newer):
```powershell
New-Item -ItemType Directory -Force out
javac -d out (Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName })
java -cp out smart_event.CoreClassTest
java -cp out smart_event.ValidationTest
```
Both test runners exit nonzero on failure. Expected results for the local Task 2.03 baseline: 25/25 and 78/78. These are test-case counts, not exhaustive coverage or proof of production readiness. Live repository consistency and team review remain pending.
