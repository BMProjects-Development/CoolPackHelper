# CoolPackHelper

Клиентский помощник для сборок Minecraft: при запуске показывает список отсутствующих модов и кнопки, ведущие на страницы скачивания.

Версия платформы: Minecraft 1.21.1, NeoForge 21.1.x, Java 21.

## Конфигурация

При первом запуске создаётся `config/coolpackhelper.json`. Два демонстрационных мода в нём отключены (`"enabled": false`), поэтому меню не появится, пока автор сборки не настроит список.

Пример записи:

```json
{
  "enabled": true,
  "name": "FTB Quests",
  "modId": "ftbquests",
  "filePattern": "ftb-quests-*.jar",
  "downloadUrl": "https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge"
}
```

Мод считается установленным, если совпал хотя бы один заполненный идентификатор:

- `modId` — идентификатор загруженного NeoForge-мода; это наиболее надёжный вариант;
- `filePattern` — имя JAR в папке `mods`, поддерживаются маски `*` и `?` без учёта регистра.

Поле `name` задаёт подпись кнопки, а `downloadUrl` — ссылку, которая открывается после стандартного подтверждения Minecraft. Разрешены только HTTP/HTTPS-ссылки.

`showOnlyOnce` работает так:

- `false` — показывать меню при каждом запуске, пока отсутствует хотя бы один включённый мод;
- `true` — показать один раз для текущей версии конфига. После изменения списка или текста меню оно сможет появиться снова. Состояние хранится отдельно в `config/coolpackhelper-state.json`.

## Локализация

Тексты задаются в `menu.translations`. Ключ — код выбранного в Minecraft языка, например `ru_ru`, `en_us` или `de_de`. Если перевода нет, используется `menu.defaultLanguage`, затем `en_us`.

В `downloadButton` доступен плейсхолдер `{mod}`. В `pageIndicator` доступны `{current}` и `{total}`. Можно добавлять любые языки без пересборки мода:

```json
"menu": {
  "defaultLanguage": "ru_ru",
  "translations": {
    "ru_ru": {
      "title": "Необходимо установить следующие моды",
      "description": "Установите моды и перезапустите игру.",
      "downloadButton": "Скачать {mod}",
      "continueButton": "Продолжить",
      "previousButton": "Назад",
      "nextButton": "Далее",
      "pageIndicator": "Страница {current} из {total}",
      "invalidLink": "Некорректная ссылка"
    }
  }
}
```

Меню поддерживает произвольное количество модов: длинный список автоматически разбивается на страницы.

## Сборка

```powershell
.\gradlew.bat build
```

Готовый JAR появится в `build/libs`.
