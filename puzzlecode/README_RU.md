# PuzzleCode v2.1 — Android
Обычный Java/Gradle Android-проект для пазлов 4×4. Исходное изображение НЕ хранится. Пользователь загружает **только перемешанную** картинку и код вида `PZ1.4.*.*` из чата.

## Как собрать APK
JDK 17, Android SDK Platform 35, Build Tools 35.0.0, Gradle 8.11.1.
Из каталога `puzzlecode` выполнить `gradle --no-daemon clean assembleDebug`.
Путь результата: `app/build/outputs/apk/debug/app-debug.apk`.
В GitHub Actions APK публикуется как artifact `PuzzleCode-Android-APK`.

## Использование
1. Установи APK. Если версии 1.0/1.1 были подписаны другим ключом, сначала их удали.
2. Нажми «Открыть PNG» и выбери неизменённый скачанный из чата файл.
3. Меняй местами фрагменты касанием или перетаскиванием.
4. Вставь код `PZ1.4.AwgPBQoCDgsJDA0BBAYHAA.9CE17885` для первого пазла и нажми «Собрать».
5. CRC32 проверяет исходные байты файла; скриншот или повторное сохранение не подходит.

## Технологии
Java, обычная Activity, Canvas View, без WebView, C++ и нативных .so.
Package: `ru.sector9.puzzlecode`; minSdk 26; targetSdk 35; compileSdk 35.
Нет зависимостей от ABI. APK debug подписывается стандартным тестовым ключом GitHub runner. Для стабильных обновлений следует использовать постоянный личный keystore (его нет в публичном репозитории).

## Диагностика
Если на телефоне закрывается при запуске: `adb logcat -d -s AndroidRuntime:E` и отправить FATAL EXCEPTION.
В CI выполняется компиляция и проверка наличия APK. Ни эмулятор, ни реальный телефон в GitHub workflow не запускаются.
