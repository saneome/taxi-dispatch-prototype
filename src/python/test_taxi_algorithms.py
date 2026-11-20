"""
test_taxi_algorithms.py - Модульные тесты алгоритмов службы заказа такси.
Покрывает:
1. Базовый расчет тарифа и граничные значения.
2. Алгоритм динамического ценообразования (Surge Pricing).
3. Геопространственный поиск по формуле гаверсинусов и ранжирование (Matching).
4. Пакетную верификацию по реестрам ФГИС «Такси» и полисам ОСГОП (ФЗ-580).
5. Сплитование финансовой комиссии.
"""

import unittest
from datetime import date, timedelta
from taxi_algorithms import (
    Tariff,
    DriverCandidate,
    calculate_base_price,
    calculate_surge_multiplier,
    calculate_final_trip_cost,
    haversine_distance,
    match_and_rank_drivers,
    verify_drivers_compliance,
    split_platform_commission
)


class TestTaxiAlgorithms(unittest.TestCase):

    def setUp(self):
        self.economy_tariff = Tariff(
            code="ECONOMY",
            name="Эконом",
            base_price=199.0,
            price_per_km=25.0,
            price_per_minute=12.0,
            minimum_price=249.0
        )
        self.today = date(2026, 11, 11)

    # ------------------------------------------------------------------------
    # 1. ТЕСТЫ БАЗОВОГО ТАРИФА И SURGE PRICING
    # ------------------------------------------------------------------------

    def test_calculate_base_price_normal(self):
        # 10 км, 20 минут: 199 + 10*25 + 20*12 = 199 + 250 + 240 = 689.0
        cost = calculate_base_price(self.economy_tariff, distance_km=10.0, duration_min=20.0)
        self.assertEqual(cost, 689.0)

    def test_calculate_base_price_minimum_threshold(self):
        # Короткая поездка: 0.5 км, 2 мин -> 199 + 12.5 + 24 = 235.5 < 249.0 (минимум)
        cost = calculate_base_price(self.economy_tariff, distance_km=0.5, duration_min=2.0)
        self.assertEqual(cost, 249.0)

    def test_calculate_surge_multiplier_balanced_or_excess_supply(self):
        # Спрос меньше предложения (D=5, S=20) -> k_surge = 1.0
        k_surge, deficit = calculate_surge_multiplier(demand=5, supply=20)
        self.assertEqual(k_surge, 1.0)
        self.assertLess(deficit, 0.0)

    def test_calculate_surge_multiplier_high_demand(self):
        # Высокий спрос: D=100, S=10, плохая погода w=1.2, пробки j=1.2
        k_surge, deficit = calculate_surge_multiplier(
            demand=100, supply=10, weather_factor=1.2, traffic_factor=1.2, k_max=3.5
        )
        self.assertGreater(k_surge, 1.0)
        self.assertLessEqual(k_surge, 3.5)

    def test_calculate_surge_multiplier_zero_supply(self):
        # Экстремальный случай: 0 свободных машин (S=0, D=50) -> срабатывает ограничение k_max
        k_surge, deficit = calculate_surge_multiplier(demand=50, supply=0, k_max=3.5)
        self.assertEqual(k_surge, 3.5)

    # ------------------------------------------------------------------------
    # 2. ТЕСТЫ ГЕОПРОСТРАНСТВЕННОГО ПОИСКА И РАНЖИРОВАНИЯ
    # ------------------------------------------------------------------------

    def test_haversine_distance_known_coordinates(self):
        # Расстояние между Кремлем (55.7520, 37.6175) и РТУ МИРЭА на Вернадского (55.6700, 37.4800)
        # Ожидаемое расстояние по ортодромии: около 12.7 - 13.5 км
        dist = haversine_distance(55.7520, 37.6175, 55.6700, 37.4800)
        self.assertAlmostEqual(dist, 13.0, delta=1.0)

    def test_match_and_rank_drivers_filters_and_scores(self):
        pickup_lat, pickup_lon = 55.7500, 37.6000

        # Водитель 1: близко (0.5 км), высокий рейтинг (4.95), доступен
        d1 = DriverCandidate(
            driver_id="D1", full_name="Иванов И.И.",
            lat=55.7540, lon=37.6020, rating=4.95,
            driving_experience_years=5, idle_minutes=20.0,
            status="AVAILABLE", vehicle_plate="А111АА777",
            vehicle_model="Haval Jolion",
            osgop_expiry_date=self.today + timedelta(days=100)
        )
        # Водитель 2: дальше (3.0 км), рейтинг ниже (4.60), доступен
        d2 = DriverCandidate(
            driver_id="D2", full_name="Петров П.П.",
            lat=55.7700, lon=37.6200, rating=4.60,
            driving_experience_years=8, idle_minutes=5.0,
            status="AVAILABLE", vehicle_plate="В222ВВ777",
            vehicle_model="Geely Coolray",
            osgop_expiry_date=self.today + timedelta(days=50)
        )
        # Водитель 3: слишком далеко (> 10 км)
        d3 = DriverCandidate(
            driver_id="D3", full_name="Сидоров С.С.",
            lat=55.6500, lon=37.5000, rating=5.00,
            driving_experience_years=10, idle_minutes=40.0,
            status="AVAILABLE", vehicle_plate="С333СС777",
            vehicle_model="Chery Tiggo 7",
            osgop_expiry_date=self.today + timedelta(days=200)
        )
        # Водитель 4: занят на заказе
        d4 = DriverCandidate(
            driver_id="D4", full_name="Козлов К.К.",
            lat=55.7510, lon=37.6010, rating=4.90,
            driving_experience_years=6, idle_minutes=0.0,
            status="ON_ORDER", vehicle_plate="Е444ЕЕ777",
            vehicle_model="Haval F7",
            osgop_expiry_date=self.today + timedelta(days=120)
        )

        drivers = [d1, d2, d3, d4]
        ranked = match_and_rank_drivers(pickup_lat, pickup_lon, drivers, max_radius_km=5.0)

        # d3 отсеян по радиусу, d4 отсеян по статусу -> осталось 2
        self.assertEqual(len(ranked), 2)
        # d1 должен быть на 1 месте из-за близости и высокого рейтинга
        self.assertEqual(ranked[0]["candidate"].driver_id, "D1")
        self.assertGreater(ranked[0]["score"], ranked[1]["score"])

    # ------------------------------------------------------------------------
    # 3. ТЕСТЫ ПАКЕТНОЙ ВЕРИФИКАЦИИ В ФГИС «ТАКСИ» И ОСГОП (ФЗ-580)
    # ------------------------------------------------------------------------

    def test_verify_drivers_compliance_filters_illegal_candidates(self):
        # Кандидат 1: полностью легитимен
        c1 = DriverCandidate(
            driver_id="D1", full_name="Легитимный Водитель",
            lat=55.75, lon=37.60, rating=4.9,
            driving_experience_years=5, idle_minutes=15.0,
            status="AVAILABLE", vehicle_plate="А111АА77",
            vehicle_model="Haval Jolion",
            osgop_expiry_date=self.today + timedelta(days=180)
        )
        # Кандидат 2: стаж менее 3 лет (нарушение ст. 12 ФЗ-580)
        c2 = DriverCandidate(
            driver_id="D2", full_name="Новичок без стажа",
            lat=55.75, lon=37.60, rating=4.8,
            driving_experience_years=1, idle_minutes=10.0,
            status="AVAILABLE", vehicle_plate="В222ВВ77",
            vehicle_model="Lada Vesta",
            osgop_expiry_date=self.today + timedelta(days=90)
        )
        # Кандидат 3: нет в реестре ФГИС (отозвано разрешение)
        c3 = DriverCandidate(
            driver_id="D3", full_name="Отозван в Минтрансе",
            lat=55.75, lon=37.60, rating=4.7,
            driving_experience_years=7, idle_minutes=25.0,
            status="AVAILABLE", vehicle_plate="С333СС77",
            vehicle_model="Geely Emgrand",
            osgop_expiry_date=self.today + timedelta(days=60)
        )
        # Кандидат 4: просрочен полис ОСГОП
        c4 = DriverCandidate(
            driver_id="D4", full_name="Просрочен полис ОСГОП",
            lat=55.75, lon=37.60, rating=4.9,
            driving_experience_years=6, idle_minutes=30.0,
            status="AVAILABLE", vehicle_plate="Е444ЕЕ77",
            vehicle_model="Chery Tiggo 4",
            osgop_expiry_date=self.today - timedelta(days=5)  # Просрочен 5 дней назад
        )

        candidates_input = [
            {"candidate": c1, "distance_km": 1.0, "score": 0.85},
            {"candidate": c2, "distance_km": 1.2, "score": 0.82},
            {"candidate": c3, "distance_km": 1.5, "score": 0.80},
            {"candidate": c4, "distance_km": 1.8, "score": 0.78},
        ]

        # Реестр ФГИС «Такси»
        fgis_registry = {
            "D1": "ACTIVE",
            "D2": "ACTIVE",
            "D3": "REVOKED",  # Отозвано!
            "D4": "ACTIVE"
        }

        verified, logs = verify_drivers_compliance(candidates_input, fgis_registry, self.today)

        # Только D1 должен пройти валидацию
        self.assertEqual(len(verified), 1)
        self.assertEqual(verified[0]["candidate"].driver_id, "D1")

        # Проверка журнала аудита
        log_dict = {entry["driver_id"]: entry["status"] for entry in logs}
        self.assertEqual(log_dict["D1"], "VERIFIED_OK")
        self.assertEqual(log_dict["D2"], "INSUFFICIENT_EXPERIENCE")
        self.assertEqual(log_dict["D3"], "PERMIT_REVOKED_OR_ABSENT")
        self.assertEqual(log_dict["D4"], "EXPIRED_OR_NO_OSGOP")

    # ------------------------------------------------------------------------
    # 4. ТЕСТЫ СПЛИТОВАНИЯ КОМИССИИ
    # ------------------------------------------------------------------------

    def test_split_platform_commission(self):
        # Поездка 1000 руб при комиссии платформы 18%
        split = split_platform_commission(final_cost=1000.0, platform_commission_rate=0.18)
        self.assertEqual(split["platform_fee"], 180.0)
        self.assertEqual(split["carrier_payout"], 820.0)
        self.assertEqual(split["platform_fee"] + split["carrier_payout"], 1000.0)


if __name__ == "__main__":
    unittest.main()
