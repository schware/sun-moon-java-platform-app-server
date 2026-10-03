rootProject.name = "sun-moon-java-platform-app-server"

// 공통 틀(실패 재처리 등) — submodule 로 받아 included build 로 붙입니다.
// 설계: umbrella docs/design/reprocess-library.md
includeBuild("java-common")
