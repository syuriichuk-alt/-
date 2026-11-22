# BrawlerClass — RPG-клас «Кулачник» (Paper/Purpur 1.21.8)

## Збирання
Потрібні JDK 21 та Maven 3.8+ (потрібен інтернет для paper-api).

    mvn clean package

Готовий файл: `target/BrawlerClass-1.0.0.jar` → покладіть у `plugins/` і перезапустіть сервер.

## Структура
    pom.xml
    src/main/resources/{plugin.yml, config.yml}
    src/main/java/ua/brawler/
      BrawlerPlugin.java            головний клас, спільні сервіси
      CooldownManager.java          персональні перезарядки (зберігаються на диск)
      command/Commands.java         /class, /brawler [reload]
      data/{PlayerData,DataManager} збереження класу, предметів, cooldown (data/<uuid>.yml)
      gui/ClassMenu.java            меню вибору класу
      gui/BrawlerMenu.java          GUI з двома спец. слотами (захист від дюпів)
      listener/CombatListener.java  кулачний бій, перенесення характеристик, пасивки зброї
      listener/AbilityListener.java щит і лук (клавіша F)
      listener/TotemListener.java   тотем зі спец. слота
      listener/PlayerListener.java  вхід/вихід, очищення
      sprint/SprintManager.java     розгін під час бігу
      util/                         Weapons, WeaponType, Fx, Msg, Items

## Команди
- `/class` — меню вибору класу (кнопка «Кулачник» + «Скинути клас»).
- `/brawler` — GUI спец. слотів (лише для Кулачника). Зліва-вгорі: основна рука (меч/сокира/булава), праворуч: допоміжна (лук/щит/тотем).
- `/brawler reload` — перезавантажити config.yml (право `brawler.admin`).

## Керування здібностями
- Кулачні удари з характеристиками зброї працюють, коли основна рука ПОРОЖНЯ.
- Щит / лук: натисніть **F** (обмін рук), коли обидві руки порожні.
  - Щит: на `shield.active-seconds` с наступна атака (типи з `shield.reflect-causes`) скасовується, уся шкода повертається нападнику. Cooldown 30 с стартує з активації.
  - Лук: випускається телепортаційна стрілка (без боєприпасів); після підтвердженого влучання (подія шкоди не скасована, шкода > 0) гравець телепортується до цілі в безпечну точку. Cooldown 30 с.
- Тотем: спрацьовує автоматично при смертельній шкоді і витрачається зі слота.

## Зміна характеристик
Усе в `plugins/BrawlerClass/config.yml` (коментарі всередині), далі `/brawler reload`:
шкода кулака, радіус сплешу меча, висота підкидання сокири, тривалість Jump Boost, cooldown щита/лука,
максимальний множник швидкості (`sprint.max-multiplier`), час розгону (`sprint.time-to-max-seconds`),
вмикання/вимикання кожної здібності (`abilities.*`), звуки й частинки (`effects.*`), повідомлення.

## Підтримувані зачарування
- Меч: Sharpness, Smite, Bane of Arthropods, Knockback, Fire Aspect, Sweeping Edge (сила сплешу).
- Сокира: Sharpness, Smite, Bane of Arthropods.
- Булава: Density, Breach, Wind Burst, Smite, Bane of Arthropods, Fire Aspect (+ ванільна шкода smash від висоти падіння).
- Лук: Power, Punch, Flame (для телепортаційної стріли).

## Обмеження (Paper API)
- Looting не діє: лут обчислюється з предмета в руці під час смерті, а зброя за умовою не в руці.
- Міцність зброї у слоті не витрачається; Unbreaking/Mending/Efficiency не потрібні.
- Ванільний щит/лук не можна використати без предмета в руці, тому активація — клавіша F.
- Під час бігу ванільна атака скидає біг (стандартна поведінка Minecraft) — розгін при цьому скидається.


## Зміни у версії 1.1
- Розгін: старт 75% -> 150% за 4 с (`sprint.start-multiplier`, `sprint.max-multiplier`).
- Плашка над хотбаром «Атака ■■■□□ / Атака готова» (`effects.attack-bar`).
- Булава: замість Jump Boost — лазіння по стінах (Shift біля стіни = висіти, Пробіл = підтягнутися).
- Сокира: підкидання на 5 блоків; наступний удар по цій цілі відкидає її на 5 блоків від вас.
- Лук: замість телепорту — стрибок до цілі на 5 блоків після влучання.
- Бафи зброї діють, лише коли ОБИДВІ руки порожні.
- Предмети зі спец. слотів випадають на місці смерті (якщо не keepInventory).
- ВАЖЛИВО: видаліть старий plugins/BrawlerClass/config.yml перед запуском нової версії.
