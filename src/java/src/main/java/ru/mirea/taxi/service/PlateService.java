package ru.mirea.taxi.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис динамической генерации и валидации регистрационных знаков РФ (ГОСТ Р 50577-93 / ГОСТ Р 50577-2018).
 * Архитектурно заимствован из d-tramp (plates.ts / cities.ts):
 * - Буквы только из 12 допустимых символов кириллицы (АВЕКМНОРСТУХ)
 * - Случайные цифры и серии букв без хардкодной привязки к модели машины
 * - Вероятностное распределение с учётом географических координат поиска такси:
 *   * В Москве: большинство — Москва (77/777/797...), реже МО и ЦФО, совсем редко — Сибирь и Дальний Восток
 *   * Во Владивостоке: большинство — Приморье (25/125), реже Хабаровск (27), Якутия (14), Иркутск (38), совсем редко — Москва
 *   * В Иркутске: большинство — Иркутск (38/138), реже Бурятия (03), Красноярск (24), Новосибирск (54), совсем редко — Москва
 */
@Service
public class PlateService {

    public static class RegionGroup {
        private final String key;
        private final String label;
        private final List<String> codes;
        private final Map<String, Double> neighbors;

        public RegionGroup(String key, String label, List<String> codes, Map<String, Double> neighbors) {
            this.key = key;
            this.label = label;
            this.codes = codes;
            this.neighbors = neighbors;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public List<String> getCodes() { return codes; }
        public Map<String, Double> getNeighbors() { return neighbors; }
    }

    public static class CityCoord {
        private final String regionKey;
        private final String regionCode;
        private final String name;
        private final double lat;
        private final double lon;

        public CityCoord(String regionKey, String regionCode, String name, double lat, double lon) {
            this.regionKey = regionKey;
            this.regionCode = regionCode;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }

        public String getRegionKey() { return regionKey; }
        public String getRegionCode() { return regionCode; }
        public String getName() { return name; }
        public double getLat() { return lat; }
        public double getLon() { return lon; }
    }

    private static final double HOME_WEIGHT = 62.0;
    private static final double RARE_WEIGHT = 1.0;
    private static final String LETTERS = "АВЕКМНОРСТУХ";
    private static final Pattern PLATE_PATTERN = Pattern.compile("^([АВЕКМНОРСТУХ])(\\d{3})([АВЕКМНОРСТУХ]{2})(\\d{2,3})$");

    private final List<RegionGroup> regionGroups = new ArrayList<>();
    private final Map<String, RegionGroup> byCode = new HashMap<>();
    private final Map<String, RegionGroup> byKey = new HashMap<>();
    private final List<CityCoord> referenceCities = new ArrayList<>();
    private final Random random = new Random();

    public PlateService() {
        initRegionGroups();
        initReferenceCities();

        for (RegionGroup g : regionGroups) {
            byKey.put(g.getKey(), g);
            for (String code : g.getCodes()) {
                byCode.put(code, g);
            }
        }
    }

    private void initRegionGroups() {
        // Москва и столичный регион
        regionGroups.add(new RegionGroup("msk", "Москва",
                List.of("77", "97", "99", "177", "197", "199", "777", "797", "799", "997"),
                Map.ofEntries(
                        Map.entry("mo", 24.0),
                        Map.entry("tver", 6.0),
                        Map.entry("kaluga", 5.0),
                        Map.entry("tula", 5.0),
                        Map.entry("ryazan", 4.0),
                        Map.entry("vladimir", 4.0),
                        Map.entry("yaroslavl", 4.0),
                        Map.entry("spb", 3.5),
                        Map.entry("nsk", 1.0),
                        Map.entry("irk", 1.0),
                        Map.entry("prim", 0.8)
                )));

        // Московская область
        regionGroups.add(new RegionGroup("mo", "Московская область",
                List.of("50", "90", "150", "190", "750", "790"),
                Map.of("msk", 38.0, "tver", 5.0, "kaluga", 5.0, "tula", 5.0, "ryazan", 5.0, "spb", 3.0, "irk", 0.8, "prim", 0.6)));

        // Города и области Центрального федерального округа
        regionGroups.add(new RegionGroup("tver", "Тверская область", List.of("69"),
                Map.of("msk", 30.0, "mo", 12.0, "kaluga", 4.0, "yaroslavl", 5.0, "spb", 5.0)));

        regionGroups.add(new RegionGroup("kaluga", "Калужская область", List.of("40"),
                Map.of("msk", 30.0, "mo", 12.0, "tula", 6.0, "bryansk", 5.0, "tver", 4.0)));

        regionGroups.add(new RegionGroup("tula", "Тульская область", List.of("71"),
                Map.of("msk", 28.0, "mo", 10.0, "kaluga", 6.0, "ryazan", 5.0, "orel", 4.0)));

        regionGroups.add(new RegionGroup("ryazan", "Рязанская область", List.of("62"),
                Map.of("msk", 26.0, "mo", 10.0, "tula", 6.0, "tambov", 5.0, "vladimir", 4.0)));

        regionGroups.add(new RegionGroup("vladimir", "Владимирская область", List.of("33"),
                Map.of("msk", 26.0, "mo", 10.0, "ivanovo", 6.0, "yaroslavl", 5.0, "ryazan", 4.0)));

        regionGroups.add(new RegionGroup("yaroslavl", "Ярославская область", List.of("76"),
                Map.of("msk", 26.0, "mo", 8.0, "kostroma", 7.0, "vladimir", 5.0, "tver", 4.0)));

        regionGroups.add(new RegionGroup("ivanovo", "Ивановская область", List.of("37"),
                Map.of("msk", 20.0, "vladimir", 7.0, "kostroma", 6.0, "yaroslavl", 5.0)));

        regionGroups.add(new RegionGroup("kostroma", "Костромская область", List.of("44"),
                Map.of("msk", 20.0, "yaroslavl", 7.0, "ivanovo", 6.0, "vladimir", 5.0)));

        regionGroups.add(new RegionGroup("smolensk", "Смоленская область", List.of("67"),
                Map.of("msk", 22.0, "tver", 6.0, "bryansk", 6.0, "kaluga", 4.0)));

        regionGroups.add(new RegionGroup("bryansk", "Брянская область", List.of("32", "132"),
                Map.of("msk", 20.0, "smolensk", 6.0, "orel", 6.0, "kaluga", 4.0)));

        regionGroups.add(new RegionGroup("orel", "Орловская область", List.of("57"),
                Map.of("msk", 20.0, "bryansk", 6.0, "tula", 5.0, "kursk", 5.0)));

        regionGroups.add(new RegionGroup("kursk", "Курская область", List.of("46", "146"),
                Map.of("msk", 18.0, "belgorod", 8.0, "orel", 6.0, "voronezh", 5.0)));

        regionGroups.add(new RegionGroup("belgorod", "Белгородская область", List.of("31", "131"),
                Map.of("msk", 18.0, "kursk", 8.0, "voronezh", 7.0)));

        regionGroups.add(new RegionGroup("voronezh", "Воронежская область", List.of("36", "136"),
                Map.of("msk", 18.0, "belgorod", 6.0, "lipetsk", 6.0, "tambov", 5.0)));

        regionGroups.add(new RegionGroup("lipetsk", "Липецкая область", List.of("48"),
                Map.of("msk", 18.0, "voronezh", 6.0, "tambov", 6.0, "tula", 4.0)));

        regionGroups.add(new RegionGroup("tambov", "Тамбовская область", List.of("68"),
                Map.of("msk", 18.0, "voronezh", 6.0, "lipetsk", 6.0, "ryazan", 5.0)));

        // Северо-Запад
        regionGroups.add(new RegionGroup("spb", "Санкт-Петербург", List.of("78", "98", "178", "198"),
                Map.of("novgorod", 16.0, "msk", 12.0, "mo", 5.0, "tver", 5.0, "irk", 1.0, "prim", 0.8)));

        regionGroups.add(new RegionGroup("novgorod", "Новгородская область", List.of("53"),
                Map.of("spb", 22.0, "msk", 10.0, "tver", 6.0)));

        // Дальний Восток: Приморье (Владивосток), Хабаровск, Якутия
        regionGroups.add(new RegionGroup("prim", "Приморский край", List.of("25", "125"),
                Map.of("khab", 20.0, "yakutia", 10.0, "irk", 7.0, "buryatia", 5.0, "krsk", 3.0, "nsk", 2.0, "msk", 1.2, "spb", 0.8)));

        regionGroups.add(new RegionGroup("khab", "Хабаровский край", List.of("27", "127"),
                Map.of("prim", 24.0, "yakutia", 12.0, "irk", 6.0, "buryatia", 4.0, "msk", 1.2)));

        regionGroups.add(new RegionGroup("yakutia", "Республика Саха (Якутия)", List.of("14"),
                Map.of("khab", 18.0, "prim", 14.0, "irk", 12.0, "buryatia", 6.0, "msk", 1.0)));

        // Сибирь: Иркутск, Красноярск, Новосибирск, Бурятия
        regionGroups.add(new RegionGroup("irk", "Иркутская область", List.of("38", "85", "138"),
                Map.of("buryatia", 20.0, "krsk", 14.0, "nsk", 10.0, "yakutia", 6.0, "prim", 4.0, "msk", 1.5, "spb", 1.0)));

        regionGroups.add(new RegionGroup("buryatia", "Республика Бурятия", List.of("03", "80", "103"),
                Map.of("irk", 25.0, "chita", 14.0, "krsk", 10.0, "nsk", 6.0, "prim", 4.0, "msk", 1.5)));

        regionGroups.add(new RegionGroup("krsk", "Красноярский край", List.of("24", "84", "88", "124"),
                Map.of("nsk", 16.0, "irk", 14.0, "buryatia", 5.0, "omsk", 4.0, "msk", 2.0)));

        regionGroups.add(new RegionGroup("nsk", "Новосибирская область", List.of("54", "154"),
                Map.of("krsk", 16.0, "irk", 12.0, "omsk", 8.0, "buryatia", 4.0, "msk", 2.5, "prim", 2.0)));
    }

    private void initReferenceCities() {
        referenceCities.add(new CityCoord("msk", "77", "Москва", 55.7558, 37.6173));
        referenceCities.add(new CityCoord("mo", "50", "Подольск (МО)", 55.4258, 37.5497));
        referenceCities.add(new CityCoord("mo", "50", "Химки (МО)", 55.8898, 37.4353));
        referenceCities.add(new CityCoord("spb", "78", "Санкт-Петербург", 59.9343, 30.3351));
        referenceCities.add(new CityCoord("novgorod", "53", "Великий Новгород", 58.5213, 31.2755));
        referenceCities.add(new CityCoord("tver", "69", "Тверь", 56.8587, 35.9176));
        referenceCities.add(new CityCoord("kaluga", "40", "Калуга", 54.5091, 36.2534));
        referenceCities.add(new CityCoord("tula", "71", "Тула", 54.1959, 37.6182));
        referenceCities.add(new CityCoord("ryazan", "62", "Рязань", 54.6297, 39.7044));
        referenceCities.add(new CityCoord("vladimir", "33", "Владимир", 56.1300, 40.4064));
        referenceCities.add(new CityCoord("ivanovo", "37", "Иваново", 57.0005, 40.9697));
        referenceCities.add(new CityCoord("yaroslavl", "76", "Ярославль", 57.6261, 39.8845));
        referenceCities.add(new CityCoord("kostroma", "44", "Кострома", 57.7273, 40.9143));
        referenceCities.add(new CityCoord("smolensk", "67", "Смоленск", 54.7818, 32.0498));
        referenceCities.add(new CityCoord("bryansk", "32", "Брянск", 53.2524, 34.3830));
        referenceCities.add(new CityCoord("orel", "57", "Орёл", 52.9651, 36.0737));
        referenceCities.add(new CityCoord("kursk", "46", "Курск", 51.7304, 36.1912));
        referenceCities.add(new CityCoord("belgorod", "31", "Белгород", 50.5999, 36.5872));
        referenceCities.add(new CityCoord("voronezh", "36", "Воронеж", 51.6606, 39.2003));
        referenceCities.add(new CityCoord("lipetsk", "48", "Липецк", 52.6103, 39.5947));
        referenceCities.add(new CityCoord("tambov", "68", "Тамбов", 52.7212, 41.4525));
        referenceCities.add(new CityCoord("krsk", "24", "Красноярск", 56.0153, 92.8932));
        referenceCities.add(new CityCoord("nsk", "54", "Новосибирск", 55.0084, 82.9357));
        referenceCities.add(new CityCoord("irk", "38", "Иркутск", 52.2870, 104.3050));
        referenceCities.add(new CityCoord("buryatia", "03", "Улан-Удэ", 51.8348, 107.5845));
        referenceCities.add(new CityCoord("yakutia", "14", "Якутск", 62.0355, 129.6755));
        referenceCities.add(new CityCoord("khab", "27", "Хабаровск", 48.4802, 135.0719));
        referenceCities.add(new CityCoord("prim", "25", "Владивосток", 43.1155, 131.8855));
    }

    /**
     * Определяет ближайший опорный регион по координатам поиска
     */
    public String findNearestRegionKey(double lat, double lon) {
        CityCoord best = null;
        double bestDist = Double.MAX_VALUE;
        for (CityCoord c : referenceCities) {
            double d = haversineKm(lat, lon, c.getLat(), c.getLon());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return (best != null) ? best.getRegionKey() : "msk";
    }

    /**
     * Определяет числовой код ближайшего региона по координатам
     */
    public String findNearestRegionCode(double lat, double lon) {
        CityCoord best = null;
        double bestDist = Double.MAX_VALUE;
        for (CityCoord c : referenceCities) {
            double d = haversineKm(lat, lon, c.getLat(), c.getLon());
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return (best != null) ? best.getRegionCode() : "77";
    }

    /**
     * Форматирует номер в стандартный читаемый вид ГОСТ: «У 394 АР 797»
     */
    public String formatPlate(String plate) {
        if (plate == null) return "";
        String clean = plate.replaceAll("\\s+", "").toUpperCase();
        Matcher m = PLATE_PATTERN.matcher(clean);
        if (m.matches()) {
            return m.group(1) + " " + m.group(2) + " " + m.group(3) + " " + m.group(4);
        }
        return plate;
    }

    /**
     * Определяет наименование субъекта РФ по коду региона в номере
     */
    public String regionLabelOf(String plate) {
        if (plate == null || plate.length() < 3) return "Россия";
        String clean = plate.replaceAll("\\s+", "").toUpperCase();

        Matcher m = PLATE_PATTERN.matcher(clean);
        String regionCode;
        if (m.matches()) {
            regionCode = m.group(4);
        } else {
            int i = clean.length() - 1;
            while (i >= 0 && Character.isDigit(clean.charAt(i))) {
                i--;
            }
            regionCode = clean.substring(i + 1);
        }

        if (regionCode.isEmpty()) return "Россия";

        RegionGroup group = byCode.get(regionCode);
        if (group != null) {
            return group.getLabel();
        }
        String stripped = regionCode.replaceFirst("^0+", "");
        if (byCode.containsKey(stripped)) {
            return byCode.get(stripped).getLabel();
        }
        return "Регион " + regionCode;
    }

    /**
     * Генерирует уникальный случайный госномер с учётом региона спавна
     */
    public String generatePlate(String spawnKeyOrCode, Set<String> excludePlates) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String regionCode = pickRegionCode(spawnKeyOrCode);
            char l1 = LETTERS.charAt(random.nextInt(LETTERS.length()));
            int num = 10 + random.nextInt(990); // 010..999
            char l2 = LETTERS.charAt(random.nextInt(LETTERS.length()));
            char l3 = LETTERS.charAt(random.nextInt(LETTERS.length()));
            String plate = String.format(Locale.US, "%c%03d%c%c%s", l1, num, l2, l3, regionCode);
            if (excludePlates == null || !excludePlates.contains(plate)) {
                return plate;
            }
        }
        return "В" + (100 + random.nextInt(899)) + "ОР" + pickRegionCode(spawnKeyOrCode);
    }

    public String generatePlate(String spawnKeyOrCode) {
        return generatePlate(spawnKeyOrCode, null);
    }

    private String pickRegionCode(String spawnKeyOrCode) {
        RegionGroup home = null;
        if (spawnKeyOrCode != null) {
            home = byKey.get(spawnKeyOrCode);
            if (home == null) {
                home = byCode.get(spawnKeyOrCode);
            }
        }
        if (home == null) {
            home = byKey.get("msk");
        }

        List<CodeWeight> candidates = new ArrayList<>();
        // 1. Домашняя группа (основная масса ~62%)
        double perHomeCode = HOME_WEIGHT / home.getCodes().size();
        for (String c : home.getCodes()) {
            candidates.add(new CodeWeight(c, perHomeCode));
        }

        // 2. Соседи (соседние области: ~35%)
        Set<String> seenGroups = new HashSet<>();
        seenGroups.add(home.getKey());
        for (Map.Entry<String, Double> neighbor : home.getNeighbors().entrySet()) {
            RegionGroup ng = byKey.get(neighbor.getKey());
            if (ng != null) {
                seenGroups.add(ng.getKey());
                double perNeighborCode = neighbor.getValue() / ng.getCodes().size();
                for (String c : ng.getCodes()) {
                    candidates.add(new CodeWeight(c, perNeighborCode));
                }
            }
        }

        // 3. Редкие дальние регионы-гости (~3%)
        for (RegionGroup g : regionGroups) {
            if (!seenGroups.contains(g.getKey())) {
                double perCode = (RARE_WEIGHT / regionGroups.size()) / g.getCodes().size();
                for (String c : g.getCodes()) {
                    candidates.add(new CodeWeight(c, perCode));
                }
            }
        }

        double totalWeight = candidates.stream().mapToDouble(c -> c.weight).sum();
        double r = random.nextDouble() * totalWeight;
        double current = 0.0;
        for (CodeWeight cw : candidates) {
            current += cw.weight;
            if (r <= current) {
                return cw.code;
            }
        }
        return home.getCodes().get(0);
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private static class CodeWeight {
        String code;
        double weight;
        CodeWeight(String code, double weight) {
            this.code = code;
            this.weight = weight;
        }
    }
}
