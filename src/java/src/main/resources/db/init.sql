-- ============================================================================
-- УЧЕБНАЯ ПРАКТИКА: РАБОТА СЛУЖБЫ ЛЕГКОВОГО ТАКСИ (РТУ МИРЭА, ИиППО)
-- База данных PostgreSQL: Физическая модель данных (3NF) по ГОСТ 7.32 / ФЗ-580
-- ============================================================================

-- 1. Перевозчики (юридические лица, ИП и самозанятые)
CREATE TABLE IF NOT EXISTS carriers (
    id BIGSERIAL PRIMARY KEY,
    legal_name VARCHAR(255) NOT NULL,
    inn VARCHAR(12) NOT NULL UNIQUE,
    ogrn VARCHAR(15),
    carrier_type VARCHAR(50) NOT NULL, -- 'LEGAL_ENTITY', 'INDIVIDUAL_ENTREPRENEUR', 'SELF_EMPLOYED'
    registry_number VARCHAR(100) NOT NULL, -- Реестровый номер во ФГИС «Такси»
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Водители
CREATE TABLE IF NOT EXISTS drivers (
    id BIGSERIAL PRIMARY KEY,
    carrier_id BIGINT REFERENCES carriers(id) ON DELETE SET NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    license_number VARCHAR(50) NOT NULL UNIQUE,
    license_issue_date DATE NOT NULL,
    rating NUMERIC(3, 2) DEFAULT 4.95,
    status VARCHAR(50) DEFAULT 'AVAILABLE', -- 'AVAILABLE', 'BUSY', 'OFFLINE'
    current_lat NUMERIC(9, 6) NOT NULL,
    current_lon NUMERIC(9, 6) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Транспортные средства
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    carrier_id BIGINT REFERENCES carriers(id) ON DELETE SET NULL,
    driver_id BIGINT REFERENCES drivers(id) ON DELETE SET NULL,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    manufacture_year INT NOT NULL,
    license_plate VARCHAR(20) NOT NULL UNIQUE,
    vin VARCHAR(20) NOT NULL UNIQUE,
    color VARCHAR(50) NOT NULL,
    registry_status VARCHAR(50) DEFAULT 'VALID',
    tariff_category VARCHAR(50) DEFAULT 'ECONOMY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Верификация водителей и ТС в государственных реестрах (ФЗ-580 / ОСГОП)
CREATE TABLE IF NOT EXISTS driver_verifications (
    id BIGSERIAL PRIMARY KEY,
    driver_id BIGINT NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    fgis_status VARCHAR(50) NOT NULL DEFAULT 'VALID', -- 'VALID', 'REJECTED', 'EXPIRED'
    fgis_check_message VARCHAR(255),
    osgop_policy_number VARCHAR(100) NOT NULL,
    osgop_status VARCHAR(50) NOT NULL DEFAULT 'VALID',
    criminal_record_check VARCHAR(50) NOT NULL DEFAULT 'CLEAN',
    last_checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Тарифные сетки
CREATE TABLE IF NOT EXISTS tariffs (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL,
    price_per_km NUMERIC(10, 2) NOT NULL,
    price_per_minute NUMERIC(10, 2) NOT NULL,
    min_price NUMERIC(10, 2) NOT NULL
);

-- 6. Заказы поездок
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    passenger_name VARCHAR(255) NOT NULL,
    passenger_phone VARCHAR(30) NOT NULL,
    driver_id BIGINT REFERENCES drivers(id) ON DELETE SET NULL,
    vehicle_id BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    tariff_code VARCHAR(50) REFERENCES tariffs(code),
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED', -- 'CREATED', 'HOLD_FUNDS', 'SEARCHING', 'ASSIGNED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    from_address VARCHAR(255) NOT NULL,
    to_address VARCHAR(255) NOT NULL,
    pickup_lat NUMERIC(9, 6) NOT NULL,
    pickup_lon NUMERIC(9, 6) NOT NULL,
    dropoff_lat NUMERIC(9, 6) NOT NULL,
    dropoff_lon NUMERIC(9, 6) NOT NULL,
    distance_meters INT DEFAULT 0,
    duration_seconds INT DEFAULT 0,
    estimated_price NUMERIC(10, 2) NOT NULL,
    final_price NUMERIC(10, 2),
    hold_id VARCHAR(100),
    surge_multiplier NUMERIC(3, 2) DEFAULT 1.0,
    route_geometry TEXT, -- GeoJSON Polyline
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- 7. Финансовые транзакции (сплитование по 54-ФЗ)
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    total_amount NUMERIC(10, 2) NOT NULL,
    platform_commission NUMERIC(10, 2) NOT NULL,
    driver_payout NUMERIC(10, 2) NOT NULL,
    payment_status VARCHAR(50) DEFAULT 'CAPTURED',
    hold_id VARCHAR(100),
    fiscal_receipt_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- НАЧАЛЬНЫЕ ДАННЫЕ (SEEDS)
-- ============================================================================

-- Перевозчики
INSERT INTO carriers (id, legal_name, inn, ogrn, carrier_type, registry_number, status)
VALUES 
    (1, 'ООО «Столичный Таксопарк»', '7701234567', '1157746123456', 'LEGAL_ENTITY', 'RU-77-REG-001294', 'ACTIVE'),
    (2, 'ИП Воронов Алексей Михайлович', '772345678901', '321774600012345', 'INDIVIDUAL_ENTREPRENEUR', 'RU-77-REG-008412', 'ACTIVE'),
    (3, 'Самозанятый Смирнов Денис Игоревич', '773456789012', NULL, 'SELF_EMPLOYED', 'RU-77-REG-019342', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- Тарифы
INSERT INTO tariffs (code, name, base_price, price_per_km, price_per_minute, min_price)
VALUES
    ('ECONOMY', 'Эконом', 149.00, 16.00, 7.00, 199.00),
    ('COMFORT', 'Комфорт', 249.00, 24.00, 11.00, 319.00),
    ('BUSINESS', 'Бизнес', 499.00, 38.00, 18.00, 599.00)
ON CONFLICT (code) DO NOTHING;

-- Водители в локациях Москвы из курсовой D-Tramp (Тверская, Арбат, Кутузовский, Центр)
INSERT INTO drivers (id, carrier_id, full_name, phone, license_number, license_issue_date, rating, status, current_lat, current_lon)
VALUES
    (1, 1, 'Михайлов Артём Сергеевич', '+7 (916) 111-22-33', '77AA123456', '2018-05-14', 4.98, 'AVAILABLE', 55.761500, 37.605200),
    (2, 1, 'Николаев Денис Олегович', '+7 (916) 222-33-44', '77BB234567', '2019-08-20', 4.92, 'AVAILABLE', 55.758000, 37.612000),
    (3, 2, 'Алексеев Владимир Игоревич', '+7 (926) 333-44-55', '77CC345678', '2017-03-10', 4.88, 'AVAILABLE', 55.753200, 37.601500),
    (4, 2, 'Кузнецов Игорь Павлович', '+7 (903) 444-55-66', '77EE456789', '2020-01-15', 4.95, 'AVAILABLE', 55.767000, 37.592000),
    (5, 3, 'Смирнов Денис Игоревич', '+7 (977) 555-66-77', '77KK567890', '2016-11-05', 5.00, 'AVAILABLE', 55.748500, 37.562000),
    (6, 1, 'Григорьев Максим Андреевич', '+7 (925) 666-77-88', '77MM678901', '2021-04-12', 4.85, 'AVAILABLE', 55.751000, 37.589000),
    (7, 1, 'Попов Роман Викторович', '+7 (915) 777-88-99', '77OO789012', '2015-09-28', 4.97, 'AVAILABLE', 55.767064, 37.613863),
    (8, 2, 'Соколов Андрей Дмитриевич', '+7 (905) 888-11-22', '50AA345123', '2019-04-11', 4.91, 'AVAILABLE', 55.756200, 37.618400),
    (9, 3, 'Морозов Сергей Васильевич', '+7 (910) 999-33-44', '40BB876543', '2017-10-02', 4.89, 'AVAILABLE', 55.764100, 37.619200),
    (10, 1, 'Васильев Павел Николаевич', '+7 (919) 222-55-66', '71CC456789', '2020-07-19', 4.93, 'AVAILABLE', 55.759400, 37.594100),
    (11, 2, 'Федоров Виктор Александрович', '+7 (981) 444-77-88', '78DD112233', '2016-02-25', 4.96, 'AVAILABLE', 55.752300, 37.615200),
    (12, 1, 'Семенов Дмитрий Алексеевич', '+7 (903) 666-88-99', '50EE998877', '2021-11-30', 4.87, 'AVAILABLE', 55.769500, 37.603100),
    (13, 3, 'Богданов Кирилл Олегович', '+7 (926) 777-00-11', '77FF554433', '2018-09-14', 4.99, 'AVAILABLE', 55.746800, 37.551200),
    (14, 2, 'Тарасов Михаил Юрьевич', '+7 (902) 111-44-55', '76GG776655', '2019-03-08', 4.90, 'AVAILABLE', 55.763400, 37.585500),
    (15, 1, 'Егоров Александр Сергеевич', '+7 (999) 333-66-77', '38HH223344', '2022-06-17', 4.94, 'AVAILABLE', 55.755100, 37.625300)
ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    phone = EXCLUDED.phone,
    license_number = EXCLUDED.license_number,
    rating = EXCLUDED.rating,
    status = EXCLUDED.status,
    current_lat = EXCLUDED.current_lat,
    current_lon = EXCLUDED.current_lon;

-- Транспортные средства с разнообразными номерами ГОСТ
INSERT INTO vehicles (id, carrier_id, driver_id, make, model, manufacture_year, license_plate, vin, color, registry_status, tariff_category)
VALUES
    (1, 1, 1, 'Haval', 'Jolion', 2023, 'В394УХ797', 'XWE12345678901234', 'Белый', 'VALID', 'COMFORT'),
    (2, 1, 2, 'Chery', 'Tiggo 4 Pro', 2022, 'К182ОМ777', 'XWE23456789012345', 'Желтый', 'VALID', 'ECONOMY'),
    (3, 2, 3, 'Geely', 'Coolray', 2023, 'С624ОН750', 'XWE34567890123456', 'Серебристый', 'VALID', 'COMFORT'),
    (4, 2, 4, 'Lada', 'Vesta NG', 2024, 'М819РС197', 'XWE45678901234567', 'Белый', 'VALID', 'ECONOMY'),
    (5, 3, 5, 'Exeed', 'TXL', 2023, 'Е941РЕ77', 'XWE56789012345678', 'Черный', 'VALID', 'BUSINESS'),
    (6, 1, 6, 'Changan', 'CS55 Plus', 2023, 'О462ХМ69', 'XWE67890123456789', 'Серый', 'VALID', 'COMFORT'),
    (7, 1, 7, 'Hongqi', 'H5', 2024, 'Х891МА777', 'XWE78901234567890', 'Черный', 'VALID', 'BUSINESS'),
    (8, 2, 8, 'Kia', 'Rio', 2022, 'Т723АВ150', 'XWE89012345678901', 'Белый', 'VALID', 'ECONOMY'),
    (9, 3, 9, 'Hyundai', 'Solaris', 2022, 'А315ЕН40', 'XWE90123456789012', 'Желтый', 'VALID', 'ECONOMY'),
    (10, 1, 10, 'Skoda', 'Rapid', 2023, 'Н684ВТ71', 'XWE01234567890123', 'Белый', 'VALID', 'ECONOMY'),
    (11, 2, 11, 'Toyota', 'Camry', 2023, 'Р527КЕ178', 'XWE11223344556677', 'Черный', 'VALID', 'COMFORT'),
    (12, 1, 12, 'Renault', 'Logan', 2022, 'У204ОС190', 'XWE22334455667788', 'Серый', 'VALID', 'ECONOMY'),
    (13, 3, 13, 'Geely', 'Monjaro', 2024, 'В739ЕМ99', 'XWE33445566778899', 'Серый', 'VALID', 'BUSINESS'),
    (14, 2, 14, 'Chery', 'Tiggo 7 Pro', 2023, 'К614НЕ76', 'XWE44556677889900', 'Синий', 'VALID', 'COMFORT'),
    (15, 1, 15, 'Lada', 'Granta', 2023, 'С508ТК138', 'XWE55667788990011', 'Белый', 'VALID', 'ECONOMY')
ON CONFLICT (id) DO UPDATE SET
    make = EXCLUDED.make,
    model = EXCLUDED.model,
    manufacture_year = EXCLUDED.manufacture_year,
    license_plate = EXCLUDED.license_plate,
    color = EXCLUDED.color,
    tariff_category = EXCLUDED.tariff_category;

-- Верификация по ФГИС «Такси» и полисам ОСГОП (ФЗ № 580-ФЗ)
INSERT INTO driver_verifications (id, driver_id, vehicle_id, fgis_status, fgis_check_message, osgop_policy_number, osgop_status, criminal_record_check)
VALUES
    (1, 1, 1, 'VALID', 'Включён в региональный реестр легковых такси г. Москвы', 'OSGOP-2026-77-99120', 'VALID', 'CLEAN'),
    (2, 2, 2, 'VALID', 'Включён в региональный реестр легковых такси г. Москвы', 'OSGOP-2026-77-88412', 'VALID', 'CLEAN'),
    (3, 3, 3, 'VALID', 'Включён в региональный реестр перевозчиков Московской области', 'OSGOP-2026-50-33190', 'VALID', 'CLEAN'),
    (4, 4, 4, 'VALID', 'Включён в региональный реестр легковых такси г. Москвы', 'OSGOP-2026-77-44122', 'VALID', 'CLEAN'),
    (5, 5, 5, 'VALID', 'Включён в региональный реестр легковых такси г. Москвы (Бизнес-класс)', 'OSGOP-2026-77-55112', 'VALID', 'CLEAN'),
    (6, 6, 6, 'VALID', 'Включён в региональный реестр перевозчиков Тверской области', 'OSGOP-2026-69-66231', 'VALID', 'CLEAN'),
    (7, 7, 7, 'VALID', 'Включён в реестр легковых такси г. Москвы (Бизнес-класс)', 'OSGOP-2026-77-77334', 'VALID', 'CLEAN'),
    (8, 8, 8, 'VALID', 'Включён в реестр перевозчиков Московской области', 'OSGOP-2026-50-88221', 'VALID', 'CLEAN'),
    (9, 9, 9, 'VALID', 'Включён в реестр легковых такси Калужской области', 'OSGOP-2026-40-99332', 'VALID', 'CLEAN'),
    (10, 10, 10, 'VALID', 'Включён в реестр легковых такси Тульской области', 'OSGOP-2026-71-10443', 'VALID', 'CLEAN'),
    (11, 11, 11, 'VALID', 'Включён в реестр легковых такси г. Санкт-Петербурга', 'OSGOP-2026-78-11554', 'VALID', 'CLEAN'),
    (12, 12, 12, 'VALID', 'Включён в реестр перевозчиков Московской области', 'OSGOP-2026-50-12665', 'VALID', 'CLEAN'),
    (13, 13, 13, 'VALID', 'Включён в реестр легковых такси г. Москвы (Бизнес-класс)', 'OSGOP-2026-77-13776', 'VALID', 'CLEAN'),
    (14, 14, 14, 'VALID', 'Включён в реестр легковых такси Ярославской области', 'OSGOP-2026-76-14887', 'VALID', 'CLEAN'),
    (15, 15, 15, 'VALID', 'Включён в реестр легковых такси Иркутской области', 'OSGOP-2026-38-15998', 'VALID', 'CLEAN')
ON CONFLICT (id) DO UPDATE SET
    fgis_status = EXCLUDED.fgis_status,
    fgis_check_message = EXCLUDED.fgis_check_message,
    osgop_policy_number = EXCLUDED.osgop_policy_number;

-- Сброс последовательностей
SELECT setval('carriers_id_seq', (SELECT MAX(id) FROM carriers));
SELECT setval('drivers_id_seq', (SELECT MAX(id) FROM drivers));
SELECT setval('vehicles_id_seq', (SELECT MAX(id) FROM vehicles));
SELECT setval('driver_verifications_id_seq', (SELECT MAX(id) FROM driver_verifications));
