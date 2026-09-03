# Причина двух дефектов подсказок, пойманная падающим тестом

Миссия `tt-tap-repro`. Ветка `version-1.6.0`, дерево на `812e4bf`. Сборка, на которой
оператор наблюдал симптомы, — 1.8.0 (`versionCode 11`).

**Рабочий код не изменён ни на строку.** В дереве появились только два файла тестов и этот
отчёт. Временная правка, которой доказывалась причина, применялась и откачена — ниже показано,
что именно применялось и что дало.

---

## Короткий ответ

Оба симптома вызывает **одна строка**, точнее — одно отсутствующее присваивание в
`RichInputConnection.deleteTextBeforeCursor` (`app/src/main/java/rkr/simplekeyboard/inputmethod/latin/RichInputConnection.java:374-386`).

Обычный backspace двигает `mExpectedSelStart` назад и **не трогает `mExpectedSelEnd`**. После
каждого нажатия backspace клавиатура считает, что в поле выделен один символ, хотя курсор на
самом деле схлопнут. Из этого прямо следуют оба симптома:

| Симптом | Через что именно |
|---|---|
| 1 — тап не вставляет слово | `InputLogic.replaceTrailingWord` отказывает на первом же гейте `mConnection.hasSelection()` (`InputLogic.java:557`) |
| 2 — подсказки не возвращаются после стирания | `LatinIME.onUpdateSelection` принимает собственный backspace клавиатуры за внешний уход курсора (`LatinIME.java:1300-1302`) и вызывает `onSelectionChanged()`, который гасит полосу и **не переспрашивает движок** |

Это ровно та «одна причина на два симптома», которую досье просило проверить, но механизм
оказался не тем, что предполагала главная версия. Про неё — отдельный раздел ниже: версия
**частично подтвердилась**, и найденный по ней дефект реален, но это **второй, независимый**
дефект, а не причина того, что видел оператор.

---

## Причина

### Что не так

`deleteTextBeforeCursor` — единственный в классе мутатор, который двигает курсор и не
схлопывает пару:

```java
public void deleteTextBeforeCursor(final int numChars) {          // :374
    String textBeforeCursor = mTextBeforeCursor;
    if (!textBeforeCursor.isEmpty() && textBeforeCursor.length() >= numChars) {
        mTextBeforeCursor = textBeforeCursor.substring(0, textBeforeCursor.length() - numChars);
    }
    if (mExpectedSelStart >= numChars) {
        mExpectedSelStart -= numChars;                             // :380  конец не трогается
    }

    if (isConnected()) {
        mIC.deleteSurroundingText(numChars, 0);
    }
}
```

Все остальные мутаторы позицию схлопывают, и делают это одинаково — `mExpectedSelEnd =
mExpectedSelStart;`: `commitText` (`:253`), `sendKeyEvent` для Enter (`:442`), для
`KEYCODE_UNKNOWN` (`:450`) и для обычного символа (`:461`). `setSelection` выставляет обе
границы явно (`:500-501`), `updateSelection` — тоже (`:113-114`).

А `hasSelection()` — это буквально сравнение пары:

```java
public boolean hasSelection() {                                   // :518
    return mExpectedSelEnd != mExpectedSelStart;
}
```

### Почему это не заметили раньше

Из семи вызовов `deleteTextBeforeCursor` пять стоят в паре с `commitText` сразу следом, в одном
batch edit, и `commitText` чинит пару строкой ниже:

* `InputLogic.java:369-370` — двойной пробел в точку;
* `InputLogic.java:408-409` — откат этого же жеста;
* `InputLogic.java:580-581` — вставка принятой подсказки (`replaceTrailingWord`);
* `InputLogic.java:628` — откат автокоррекции.

Без парного `commitText` остаются ровно два вызова, и оба — это **чистый backspace**:

```java
final int emojiClusterLength = EmojiTextUtils.trailingEmojiClusterLength(...);
if (emojiClusterLength > 0) {
    mConnection.deleteTextBeforeCursor(emojiClusterLength);       // InputLogic.java:423
} else {
    final int codePointBeforeCursor = mConnection.getCodePointBeforeCursor();
    if (codePointBeforeCursor == Constants.NOT_A_CODE) {
        sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL);
    } else {
        final int numChars = Character.isSupplementaryCodePoint(codePointBeforeCursor) ? 2 : 1;
        mConnection.deleteTextBeforeCursor(numChars);             // InputLogic.java:430
    }
}
```

То есть дефект виден **только после backspace, не завершённого вставкой**, — и это ровно тот
жест, который оператор описал в симптоме 2.

### Как это гасит тап (симптом 1)

Единственный путь вставки принятой подсказки — `replaceTrailingWord`, и его первая содержательная
проверка:

```java
if (mConnection.hasSelection()) {                                 // InputLogic.java:557
    return false;
}
```

Пока пара рассинхронизирована, `hasSelection()` возвращает `true`, и тап отвергается **до** того,
как код вообще посмотрит на слово перед курсором. Полоса при этом ничего не знает про отказ:
`onTap` вызывает `editor.commitSuggestion(...)`, получает `false` и просто ничего не делает —
`displayedPrefix` не сбрасывается, `strip.reserve()` не вызывается
(`SuggestionsController.kt:1511-1516`). Поэтому ячейка подсвечивается (это чистая работа вьюхи,
`SuggestionStripView.onTouchEvent`), слова остаются на месте, а текст не меняется.

**Скриншоты оператора это подтверждают, и довольно точно.** На
`1.8.1-tap-after-release.jpg` три кандидата `синең | сине | сингапур` соответствуют живому
хвостовому слову `сине` в поле. Если бы тап гасился «отвязкой кандидатов» (главная версия
досье), полоса показывала бы кандидатов для **другого**, уже устаревшего префикса. Они
совпадают — значит контроллер был связан, `commitSuggestion` был вызван, и отказал именно
редактор. Курсор на скриншоте схлопнут: расходится не реальное выделение, а представление
клавиатуры о нём.

### Как это гасит подсказки (симптом 2)

`LatinIME.onUpdateSelection` отличает свой ход от чужого сравнением обеих границ:

```java
final boolean externalMove =
        newSelStart != mInputLogic.mConnection.getExpectedSelectionStart()
                || newSelEnd != mInputLogic.mConnection.getExpectedSelectionEnd();   // :1300-1302

mInputLogic.onUpdateSelection(newSelStart, newSelEnd);
if (externalMove && mSuggestionsController != null) {
    mSuggestionsController.onSelectionChanged();                                     // :1306
}
```

После backspace на позиции 10 ожидается `(9, 10)`, а система рапортует `(9, 9)`. Концы не
совпали — собственный backspace клавиатуры классифицируется как внешний уход курсора, и
вызывается `onSelectionChanged()`. А он (`SuggestionsController.kt:599-621`) инкрементирует
`sessionId`, вызывает `engine.finishInput()` (это обнуляет `currentToken`, то есть выбрасывает
уже летящий результат), сбрасывает `displayedPrefix`/`displayedContextWord`, гасит полосу в
пустую ленту — **и не отправляет нового запроса**.

Результат: запрос на восстановленный префикс `како` уходит, но его ответ отбрасывается
`applyResult` (`SuggestionsController.kt:1313-1315`) — либо по `sessionId != requestSessionId`,
либо по `!isCurrent(token)`, смотря что успело раньше. Полоса остаётся пустой, и переспросить
её некому до следующего нажатия клавиши. Поэтому подсказки на том же самом префиксе, который
секунду назад их показывал, не появляются.

Порядок прихода не спасает: если ответ успел раньше и полоса уже нарисована, `onSelectionChanged()`
приходит следом и стирает её. Оба порядка проверены отдельными тестами (см. ниже) и оба дают
пустую полосу — поэтому дефект воспроизводится каждый раз, а не изредка.

---

## Падающие тесты

Два новых файла, оба — обычные JVM-тесты, Android-объекты не создаются.

### 1. `app/src/test/java/rkr/simplekeyboard/inputmethod/latin/BackspaceSelectionDesyncTest.kt`

Причина в чистом виде, на настоящем `RichInputConnection`.

```
BackspaceSelectionDesyncTest > backspaceLeavesTheCursorCollapsed FAILED
java.lang.AssertionError: selection end must follow the start expected:<9> but was:<10>

BackspaceSelectionDesyncTest > aTapIsNotRejectedByAPhantomSelectionAfterBackspace FAILED
java.lang.AssertionError: replaceTrailingWord() returns false at its hasSelection() gate, so the tap commits nothing

BackspaceSelectionDesyncTest > theKeyboardsOwnBackspaceIsNotReportedAsAnExternalCursorMove FAILED
java.lang.AssertionError: the keyboard's own backspace is classified as an external cursor move, which drops the in-flight lookup and clears the band
```

### 2. `app/src/test/java/rkr/simplekeyboard/inputmethod/latin/suggestions/TapReproTest.kt`

Сценарии оператора целиком, через контроллер. Про рассинхрон здесь **не утверждается ничего** —
тест просто гоняет настоящий `RichInputConnection` и настоящую формулу `externalMove` из
`LatinIME` и даёт им самим решить, вызывать ли `onSelectionChanged()`.

```
TapReproTest > suggestionsComeBackAfterDeletingTheExtraLetter FAILED
java.lang.AssertionError: the same prefix that had candidates one keystroke ago shows none
    expected:<[какой, какое, какая]> but was:<[]>

TapReproTest > suggestionsSurviveTheCursorReportThatFollowsABackspace FAILED
java.lang.AssertionError: the band is wiped by the keyboard's own backspace and nothing re-requests
    expected:<[какой, какое, какая]> but was:<[]>

TapReproTest > tappingAWordTheStripIsPaintingAlwaysReachesTheEditor FAILED
java.lang.AssertionError: a painted word was tapped and nothing reached the editor expected:<1> but was:<0>
```

Третий тест — про **второй** дефект, он ниже отдельным разделом.

### Почему прежние тесты этого не ловили

`FakeEngine` в `SuggestionsControllerTest` (`:97-134`) отдаёт на каждый запрос **одну и ту же
константу** `TOKEN`, а `isCurrent()` у него возвращает поле `isCurrentResult`, по умолчанию
`true`. Такой фейк физически не умеет выразить «результат выброшен, потому что поколение
сменилось» — а именно в этом состоянии живут оба симптома. Поэтому в `TapReproTest` заведён
`LatestOnlyEngine`, повторяющий семантику `LatestOnlyPrefixEngine`: свежий токен на каждый
запрос, `isCurrent` только для новейшего, `finishInput()` обнуляет поколение.

Это, скорее всего, и есть ответ на вопрос, почему предыдущая миссия закрыла обе гипотезы как
«защита работает правильно»: защита действительно написана правильно, но проверялась она на
фейке, в котором нечему было сработать.

---

## Доказательство причины

Временная правка (применена, проверена, **откачена** — в дереве её нет):

```java
if (mExpectedSelStart >= numChars) {
    mExpectedSelStart -= numChars;
}
mExpectedSelEnd = mExpectedSelStart;   // временно, только ради доказательства
```

Результат `./gradlew :app:testDebugUnitTest --offline` с этой правкой:

```
TapReproTest > tappingAWordTheStripIsPaintingAlwaysReachesTheEditor FAILED
815 tests completed, 1 failed
```

То есть: все три теста причины и оба теста симптома 2 позеленели, **регрессий по всей сюите из
815 тестов нет ни одной**, и красным остался ровно один тест — про второй, независимый дефект,
которого эта правка не касается. После отката все шесть снова падают.

---

## Способ починки (не выполнен)

Чинить эту миссия мандата не имела; ниже — что делать, словами.

**Основное.** В `deleteTextBeforeCursor` схлопывать пару так же, как это делают все остальные
мутаторы класса. Правка на одну строку, и её лучше поставить под `hasCursorPosition()`, как
сделано в `commitText` (`:251-254`), чтобы не превращать `INVALID_CURSOR_POSITION` в осмысленное
число:

```java
if (hasCursorPosition()) {
    if (mExpectedSelStart >= numChars) {
        mExpectedSelStart -= numChars;
    }
    mExpectedSelEnd = mExpectedSelStart;
}
```

Схлопывание здесь безопасно: `handleBackspaceEvent` уходит в `deleteSelectedText()`, когда
выделение настоящее (`InputLogic.java:399`), а три оставшихся вызова
(`replaceTrailingWord`, откат автокоррекции, откат двойного пробела) сами требуют схлопнутого
курсора. То есть в `deleteTextBeforeCursor` мы попадаем только с курсором, и держать конец
отдельно от начала тут нечему.

**Стоит рассмотреть отдельно, но это уже не про эти два симптома.** `onSelectionChanged()`
гасит полосу и не переспрашивает движок, тогда как `onStartInput` и `onSubtypeChanged` в
похожей ситуации переспрашивают (`SuggestionsController.kt:588-590`, `:688-690`). После
основной правки внешний уход курсора останется единственным путём, на котором полоса пустеет
без переспроса. Это не дефект по контракту (внешний уход — законная причина всё сбросить), но
асимметрия заметная, и решать по ней оператору.

---

## Третье следствие той же причины — не воспроизведено, проверить

Выведено из чтения, на устройстве **не проверялось**, поэтому идёт отдельно и без утверждений.

`handleBackspaceEvent` начинается с ветвления по `hasSelection()`:

```java
if (mConnection.hasSelection()) {          // InputLogic.java:399
    mJustDoubleSpaced = false;
    mConnection.deleteSelectedText();
} else {
    ...
}
```

Пока пара рассинхронизирована, **второй** backspace, пришедший раньше системного
`onUpdateSelection`, уйдёт в `deleteSelectedText()`. А тот делает
`setSelection(mExpectedSelStart, mExpectedSelStart)` и затем
`mIC.deleteSurroundingText(0, selectionLength)` (`RichInputConnection.java:394-399`) — то есть
удаляет символ **после** курсора. При удержании backspace (автоповтор) окно между нажатиями
как раз короче типичной задержки `onUpdateSelection`.

Если это подтвердится на устройстве — это порча текста, и она делает основную правку срочной,
а не косметической. Проверять зажатым backspace в середине строки: пропадают ли символы справа
от курсора.

---

## Главная версия досье: что с ней стало

Версия была — «полоса продолжает показывать кандидатов, которых контроллер уже отвязал».

**Как причина симптомов оператора — отвергнута**, и вот чем. На
`1.8.1-tap-after-release.jpg` показанные кандидаты соответствуют живому хвостовому слову
`сине`. Отвязка в `requestCurrentPrefix` происходит ровно тогда, когда живое слово
**перестало** совпадать с показанным (`SuggestionsController.kt:1186-1188`), — при отвязке на
полосе висели бы кандидаты другого префикса. Кроме того, отвязка не объясняет симптом 2 вообще:
там полоса пуста, а не населена мёртвыми словами.

**Как самостоятельный дефект — подтверждена**, и это второй результат миссии. Тест
`tappingAWordTheStripIsPaintingAlwaysReachesTheEditor` падает и после основной правки:

```java
if (word != displayedPrefix) {
    displayedPrefix = null;     // SuggestionsController.kt:1186-1188
}
```

Кандидаты отвязываются, а полоса не перерисовывается — до прихода нового результата
пользователь видит живые кнопки, которые гарантированно ничего не сделают. Комментарий в коде
описывает отвязку как защиту от вставки устаревшего кандидата, и как защита она работает; не
сделано второе — не убрано с экрана то, что стало неактивным. Обычно окно длиной в один ответ
движка, но если ответ не придёт (любой из гейтов `applyResult`), оно не закрывается.

Чинить это одной правкой с основной не стоит: тут выбор, а не очевидность. Либо гасить слова
вместе с отвязкой (`strip.reserve()` рядом с `displayedPrefix = null`) — тогда полоса будет
мигать на каждой букве; либо оставить как есть и признать окно допустимым. Это решение
оператора, и оно про UX, а не про корректность.

---

## Что проверено и отвергнуто

Чтобы следующий не тратил на это время повторно.

* **Потеря tap-слушателя.** Не подтвердилась и здесь. Слушатель перевешивается в
  `onStartInput`, `onSubtypeChanged` и `publishEngine`; на скриншоте нажатие вообще дошло до
  отрисовки, а `SuggestionStripView.release()` гасит и слова тоже — пустая полоса, а не мёртвая.
* **Коалесинг в `LatestOnlyPrefixEngine`.** Читался целиком; `drain()` корректно забирает
  `pendingRequest` под тем же локом, которым уходит в простой, а `finally →
  recoverUnexpectedWorkerExit` закрывает выход по исключению. Состояния «`activeWorkerId`
  завис ненулевым, воркер не запустится больше никогда» найти не удалось.
* **`runEmptyResultPrefixLength` как фильтр запросов.** Не фильтр: он читается только в
  `reportCompletionIfClean` (`SuggestionsController.kt:1288-1293`), то есть влияет на обучение
  личного словаря, а не на то, отправлять ли запрос.
* **`onTextChanged` не вызывается после backspace.** Проверено по коду до конца, как просило
  досье: `onEvent` → `mInputLogic.onCodeInput` → `updateStateAfterInputTransaction`
  (`LatinIME.java:1596-1598`) → `mSuggestionsController.onTextChanged()` (`:1857-1859`), без
  условий на вид события. Вызывается. Запрос на восстановленный префикс действительно уходит —
  выбрасывается его **ответ**.
* **Восемь находок `docs/SILENT-AUDIT.md` (A1-A5, B1-B3).** Не трогались, к этим симптомам
  отношения не имеют — как и сказано в досье.

---

## Об уточнении «тап не работал везде»

Досье просило, чтобы версия объяснила живучесть отказа через смену поля ввода, иначе она
неполна. Здесь нужна аккуратность, потому что найденная причина этого **не объясняет**, и
подгонять её не стоит.

Рассинхрон лечится сам, тремя разными способами: следующим `onUpdateSelection`
(`updateSelection` выставляет обе границы), следующим набранным символом (`commitText`
схлопывает пару) и сменой поля — `onFinishInputView` вызывает `mInputLogic.clearCaches()`
(`LatinIME.java:1107`), а `clearCaches()` ставит обе границы в `INVALID_CURSOR_POSITION`
(`RichInputConnection.java:230-237`), то есть снова равными. Через `onFinishInput` +
`onStartInput` это состояние **не переживает**.

Но наблюдением было «тап не вставлял ни в одном поле», а «состояние пережило смену поля» —
это уже вывод из наблюдения. У наблюдения есть второе прочтение, и найденной причине оно
соответствует точно: **дефект заново возникает в каждом поле, потому что это состояние
клавиатуры, а не приложения.** Любой backspace в любом поле открывает окно, и пока оно
открыто, тап отвергается. Отсюда и «тап работает обычно» (окно короткое, обычно закрывается
раньше, чем палец дойдёт до полосы), и «не работал везде» (в Telegram нет ничего особенного).

Проверить это на устройстве дёшево и стоит сделать до правки: набрать слово, нажать backspace
и **сразу** тапнуть по подсказке — тап должен не сработать; затем набрать слово, ничего не
стирая, и тапнуть — должен сработать. Если разница есть, причина названа верно. Если тап не
работает и без предшествующего backspace, значит живёт что-то ещё, и вот тогда искать надо
там, куда указывало уточнение, — в состоянии, переживающем редакторскую сессию.

---

## Стоимость

Одна сессия, без перезапусков. Сборок APK не делалось, эмулятор не поднимался, наружу ничего
не уходило, ключ подписи не трогался. Прогонов сюиты — четыре (два точечных, два полных).

ПРИЧИНА НАЙДЕНА
