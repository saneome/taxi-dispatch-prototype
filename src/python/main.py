"""
main.py - Демонстрационный запуск сквозного сценария выполнения заказа такси.
Учебная практика: «Работа службы легкового такси» (РТУ МИРЭА, ИКБО-13-24).
"""

from datetime import date, timedelta
from taxi_algorithms import (
    Tariff,
    DriverCandidate,
    calculate_final_trip_cost,
    match_and_rank_drivers,
    verify_drivers_compliance,
    split_platform_commission
)


def run_demo():
    print("=" * 80)
    print(" ДЕМОНСТРАЦИЯ АЛГОРИТМИЧЕСКОГО ОБЕСПЕЧЕНИЯ СЛУЖБЫ ЗАКАЗА ТАКСИ (ФЗ-580)")
    print("=" * 80)

    # 1. Инициализация тарифа
    tariff = Tariff(
        code="COMFORT",
        name="Комфорт",
        base_price=249.0,
        price_per_km=32.0,
        price_per_minute=14.0,
        minimum_price=299.0
    )
    print(f"\n[1] Выбран тариф: {tariff.name} (подача {tariff.base_price} руб, {tariff.price_per_km} руб/км)")

    # 2. Параметры поездки и расчет Surge Pricing
    distance_km = 12.4
    duration_min = 26.0
    demand = 45   # 45 активных заказов в районе
    supply = 8    # 8 свободных водителей
    weather = 1.15  # Дождь (+15%)
    traffic = 1.20  # Пробки 7 баллов (+20%)

    pricing = calculate_final_trip_cost(
        tariff=tariff,
        distance_km=distance_km,
        duration_min=duration_min,
        demand=demand,
        supply=supply,
        weather_factor=weather,
        traffic_factor=traffic
    )

    print(f"\n[2] Расчет стоимости поездки:")
    print(f"    - Расстояние: {distance_km} км, Длительность: {duration_min} мин")
    print(f"    - Локальный спрос: {demand} заказов, Предложение: {supply} машин")
    print(f"    - Базовая стоимость: {pricing['base_cost']} руб.")
    print(f"    - Коэффициент Surge: x{pricing['surge_multiplier']} (дефицит: {pricing['deficit_ratio']})")
    print(f"    - Итого к оплате (холдирование): {pricing['final_cost']} руб.")

    # 3. Пул водителей в системе
    pickup_lat, pickup_lon = 55.751244, 37.618423  # Центр Москвы
    today = date(2026, 11, 11)

    drivers_pool = [
        DriverCandidate("D101", "Алексеев А.А.", 55.7530, 37.6190, 4.96, 7, 25.0, "AVAILABLE", "Е777КХ777", "Haval Jolion", today + timedelta(days=210)),
        DriverCandidate("D102", "Борисов Б.Б.", 55.7600, 37.6250, 4.88, 4, 10.0, "AVAILABLE", "М123ММ777", "Geely Coolray", today + timedelta(days=120)),
        DriverCandidate("D103", "Васильев В.В.", 55.7520, 37.6170, 4.75, 1, 40.0, "AVAILABLE", "С456СС777", "Chery Tiggo 7", today + timedelta(days=90)),  # Стаж < 3 лет!
        DriverCandidate("D104", "Григорьев Г.Г.", 55.7540, 37.6200, 4.92, 12, 15.0, "AVAILABLE", "В789ВВ777", "Lada Vesta", today - timedelta(days=10)),  # Просрочен ОСГОП!
        DriverCandidate("D105", "Дмитриев Д.Д.", 55.7550, 37.6150, 4.85, 9, 30.0, "AVAILABLE", "О321ОО777", "Haval F7", today + timedelta(days=150)),
        DriverCandidate("D106", "Егоров Е.Е.", 55.8200, 37.7000, 4.99, 15, 5.0, "AVAILABLE", "Т999ТТ777", "Geely Monjaro", today + timedelta(days=300)), # Далеко (> 10 км)
        DriverCandidate("D107", "Жуков Ж.Ж.", 55.7515, 37.6180, 4.90, 6, 0.0, "ON_ORDER", "Р555РР777", "Chery Tiggo 4", today + timedelta(days=180))      # Занят!
    ]

    print(f"\n[3] Геопоиск и ранжирование (Matching):")
    print(f"    - Всего зарегистрировано водителей: {len(drivers_pool)}")
    ranked = match_and_rank_drivers(pickup_lat, pickup_lon, drivers_pool, max_radius_km=5.0)
    print(f"    - Найдено доступных в радиусе 5 км: {len(ranked)}")
    for i, c in enumerate(ranked, 1):
        d = c["candidate"]
        print(f"      {i}. {d.full_name} ({d.vehicle_model}, {d.vehicle_plate}) | "
              f"Дистанция: {c['distance_km']} км | ETA: {c['estimated_eta_min']} мин | "
              f"Рейтинг: {d.rating} | Скор: {c['score']}")

    # 4. Верификация в ФГИС «Такси» и ОСГОП
    fgis_registry = {
        "D101": "ACTIVE",
        "D102": "ACTIVE",
        "D103": "ACTIVE",
        "D104": "ACTIVE",
        "D105": "REVOKED",  # Отозвано разрешение!
        "D106": "ACTIVE",
        "D107": "ACTIVE"
    }

    print(f"\n[4] Пакетная верификация по реестрам ФГИС «Такси» и полисам ОСГОП (ФЗ-580):")
    verified, audit_logs = verify_drivers_compliance(ranked, fgis_registry, today)

    for log in audit_logs:
        icon = "[v] ДОПУЩЕН" if log["is_valid"] else "[X] ОТКЛОНЕН"
        print(f"    - {icon}: {log['full_name']} (ID: {log['driver_id']}) -> Причина: {log['status']}")

    print(f"\n    => Итого допущено к передаче заказа: {len(verified)} водитель(ей)")

    if verified:
        best_candidate = verified[0]
        selected_driver = best_candidate["candidate"]
        print(f"\n[5] Назначение лучшего кандидата:")
        print(f"    - Водитель: {selected_driver.full_name}")
        print(f"    - Автомобиль: {selected_driver.vehicle_model} ({selected_driver.vehicle_plate})")
        print(f"    - Время подачи: {best_candidate['estimated_eta_min']} мин.")

        # 6. Завершение поездки и сплитование комиссии
        split = split_platform_commission(pricing["final_cost"], platform_commission_rate=0.18)
        print(f"\n[6] Завершение поездки и сплитование комиссии (18% / 82%):")
        print(f"    - Списано с карты пассажира: {split['final_cost']} руб.")
        print(f"    - Доход платформы-агрегатора (18%): {split['platform_fee']} руб.")
        print(f"    - Выплата перевозчику / водителю (82%): {split['carrier_payout']} руб.")
        print(f"    - Фискальный чек доставлен в приложение пассажира.")

    print("\n" + "=" * 80)
    print(" СКВОЗНОЙ СЦЕНАРИЙ УСПЕШНО ЗАВЕРШЁН")
    print("=" * 80)


if __name__ == "__main__":
    run_demo()
