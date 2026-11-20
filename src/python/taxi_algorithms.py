"""
taxi_algorithms.py - Процедурная реализация ключевых алгоритмов службы заказа такси.
Разработано в рамках учебной практики (Этап 3, РТУ МИРЭА).
Соответствует требованиям ФЗ-580 и ГОСТ 19.701-90.
"""

import math
from datetime import date
from typing import List, Dict, Tuple, Optional, Any


# ============================================================================
# 1. СТРУКТУРЫ ДАННЫХ (в соответствии с ERD модели данных)
# ============================================================================

class Tariff:
    """Тарифный план службы заказа такси."""
    def __init__(
        self,
        code: str,
        name: str,
        base_price: float,
        price_per_km: float,
        price_per_minute: float,
        minimum_price: float,
        cancellation_fee: float = 100.0
    ):
        self.code = code
        self.name = name
        self.base_price = base_price
        self.price_per_km = price_per_km
        self.price_per_minute = price_per_minute
        self.minimum_price = minimum_price
        self.cancellation_fee = cancellation_fee


class DriverCandidate:
    """Кандидат-водитель с характеристиками автомобиля и статусом."""
    def __init__(
        self,
        driver_id: str,
        full_name: str,
        lat: float,
        lon: float,
        rating: float,
        driving_experience_years: int,
        idle_minutes: float,
        status: str,
        vehicle_plate: str,
        vehicle_model: str,
        osgop_expiry_date: date
    ):
        self.driver_id = driver_id
        self.full_name = full_name
        self.lat = lat
        self.lon = lon
        self.rating = rating
        self.driving_experience_years = driving_experience_years
        self.idle_minutes = idle_minutes
        self.status = status  # 'AVAILABLE', 'ON_ORDER', 'OFFLINE', 'BLOCKED'
        self.vehicle_plate = vehicle_plate
        self.vehicle_model = vehicle_model
        self.osgop_expiry_date = osgop_expiry_date


# ============================================================================
# 2. АЛГОРИТМ 1: ДИНАМИЧЕСКОЕ ЦЕНООБРАЗОВАНИЕ (SURGE PRICING)
# ============================================================================

def calculate_base_price(
    tariff: Tariff,
    distance_km: float,
    duration_min: float
) -> float:
    """
    Расчёт базовой стоимости поездки по фиксированному тарифу.
    C_base = max(C_min, C_start + d * p_km + t * p_min)
    Сложность: O(1) time, O(1) space.
    """
    variable_cost = (distance_km * tariff.price_per_km) + (duration_min * tariff.price_per_minute)
    raw_cost = tariff.base_price + variable_cost
    return round(max(tariff.minimum_price, raw_cost), 2)


def calculate_surge_multiplier(
    demand: int,
    supply: int,
    weather_factor: float = 1.0,
    traffic_factor: float = 1.0,
    k_max: float = 3.5,
    alpha: float = 0.5
) -> Tuple[float, float]:
    """
    Расчёт коэффициента динамического ценообразования (Surge Multiplier).
    k_surge = clamp(1.0, k_max, 1.0 + alpha * ((D - S) / (S + 1.0)) * w * j)

    Возвращает: (k_surge, deficit_ratio)
    Сложность: O(1) time, O(1) space.
    """
    if supply <= 0:
        deficit_ratio = float(demand)
    else:
        deficit_ratio = (demand - supply) / (supply + 1.0)

    if deficit_ratio <= 0.0:
        return 1.0, deficit_ratio

    raw_k = 1.0 + (alpha * deficit_ratio * weather_factor * traffic_factor)
    k_surge = max(1.0, min(k_max, raw_k))
    return round(k_surge, 2), round(deficit_ratio, 3)


def calculate_final_trip_cost(
    tariff: Tariff,
    distance_km: float,
    duration_min: float,
    demand: int,
    supply: int,
    weather_factor: float = 1.0,
    traffic_factor: float = 1.0
) -> Dict[str, Any]:
    """
    Полный расчет стоимости поездки с учетом базового тарифа и surge pricing.
    """
    base_cost = calculate_base_price(tariff, distance_km, duration_min)
    k_surge, deficit = calculate_surge_multiplier(
        demand=demand,
        supply=supply,
        weather_factor=weather_factor,
        traffic_factor=traffic_factor
    )
    final_cost = round(base_cost * k_surge, 2)
    return {
        "base_cost": base_cost,
        "surge_multiplier": k_surge,
        "deficit_ratio": deficit,
        "final_cost": final_cost
    }


# ============================================================================
# 3. АЛГОРИТМ 2: ГЕОПРОСТРАНСТВЕННЫЙ ПОИСК И РАНЖИРОВАНИЕ ВОДИТЕЛЕЙ
# ============================================================================

def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """
    Вычисление ортодромического расстояния между двумя координатами по формуле гаверсинусов.
    Возвращает расстояние в километрах.
    Сложность: O(1) time, O(1) space.
    """
    r_earth = 6371.0  # Средний радиус Земли в км
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (math.sin(delta_phi / 2.0) ** 2 +
         math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2)
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r_earth * c


def match_and_rank_drivers(
    pickup_lat: float,
    pickup_lon: float,
    drivers: List[DriverCandidate],
    max_radius_km: float = 5.0,
    w_distance: float = 0.50,
    w_rating: float = 0.35,
    w_idle: float = 0.15,
    max_idle_minutes: float = 60.0
) -> List[Dict[str, Any]]:
    """
    Фильтрация и ранжирование доступных водителей по комплексному скору:
    Score = w_d * (1 - d/R_max) + w_r * ((rating - 1) / 4) + w_t * min(1, idle / T_max)

    Сложность: O(N + K log K) time, где N = len(drivers), K = число подходящих.
               O(K) space.
    """
    candidates = []

    for d in drivers:
        if d.status != "AVAILABLE":
            continue

        dist_km = haversine_distance(pickup_lat, pickup_lon, d.lat, d.lon)
        if dist_km > max_radius_km:
            continue

        # Нормализация метрик в диапазон [0.0, 1.0]
        norm_distance = 1.0 - (dist_km / max_radius_km)
        norm_rating = max(0.0, min(1.0, (d.rating - 1.0) / 4.0))
        norm_idle = max(0.0, min(1.0, d.idle_minutes / max_idle_minutes))

        score = (w_distance * norm_distance) + (w_rating * norm_rating) + (w_idle * norm_idle)
        score = round(score, 4)

        candidates.append({
            "candidate": d,
            "distance_km": round(dist_km, 3),
            "estimated_eta_min": round(dist_km / 35.0 * 60.0, 1),  # Средняя городская скорость 35 км/ч
            "score": score
        })

    # Сортировка по убыванию многокритериального скора
    candidates.sort(key=lambda item: item["score"], reverse=True)
    return candidates


# ============================================================================
# 4. АЛГОРИТМ 3: ПАКЕТНАЯ ВЕРИФИКАЦИЯ В ФГИС «ТАКСИ» И ОСГОП (ФЗ-580)
# ============================================================================

def verify_drivers_compliance(
    candidates: List[Dict[str, Any]],
    fgis_registry: Dict[str, str],
    current_date: date,
    min_experience_years: int = 3
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """
    Пакетная проверка кандидатов на соответствие нормам ФЗ-580:
    1. Стаж вождения >= 3 лет.
    2. Активное разрешение в региональном реестре ФГИС «Такси» (status == 'ACTIVE').
    3. Действующий полис обязательного страхования ОСГОП (expiry >= current_date).

    Возвращает: (verified_candidates, audit_logs)
    Сложность: O(K) time, O(K) space.
    """
    verified_candidates = []
    audit_logs = []

    for item in candidates:
        d: DriverCandidate = item["candidate"]
        driver_id = d.driver_id
        is_valid = True
        rejection_reason = "VERIFIED_OK"

        # 1. Проверка стажа вождения
        if d.driving_experience_years < min_experience_years:
            is_valid = False
            rejection_reason = "INSUFFICIENT_EXPERIENCE"

        # 2. Проверка статуса в реестре ФГИС «Такси»
        elif fgis_registry.get(driver_id) != "ACTIVE":
            is_valid = False
            rejection_reason = "PERMIT_REVOKED_OR_ABSENT"

        # 3. Проверка полиса ОСГОП
        elif d.osgop_expiry_date < current_date:
            is_valid = False
            rejection_reason = "EXPIRED_OR_NO_OSGOP"

        # Формирование записи аудита (для сохранения в DRIVER_VERIFICATIONS)
        audit_entry = {
            "driver_id": driver_id,
            "full_name": d.full_name,
            "is_valid": is_valid,
            "status": rejection_reason,
            "fgis_status": fgis_registry.get(driver_id, "NOT_FOUND"),
            "osgop_expiry": d.osgop_expiry_date.isoformat(),
            "checked_at": current_date.isoformat()
        }
        audit_logs.append(audit_entry)

        if is_valid:
            verified_candidates.append(item)

    return verified_candidates, audit_logs


# ============================================================================
# 5. СКВОЗНОЙ СЦЕНАРИЙ И БИЛЛИНГ (СПЛИТОВАНИЕ КОМИССИИ)
# ============================================================================

def split_platform_commission(
    final_cost: float,
    platform_commission_rate: float = 0.18
) -> Dict[str, float]:
    """
    Расчёт сплитования средств: удержание комиссии платформы и выплата перевозчику.
    Сложность: O(1) time, O(1) space.
    """
    platform_fee = round(final_cost * platform_commission_rate, 2)
    carrier_payout = round(final_cost - platform_fee, 2)
    return {
        "final_cost": final_cost,
        "commission_rate": platform_commission_rate,
        "platform_fee": platform_fee,
        "carrier_payout": carrier_payout
    }
