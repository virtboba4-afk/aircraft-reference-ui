-- V3: предметные справочники с настоящими FK; не меняет применённые V1/V2.
-- Демонстрационные аэропорты, стоянки и продолжительности НЕ являются эксплуатационными данными.
CREATE TABLE airports (
  id UUID PRIMARY KEY,
  iata_code VARCHAR(3) NOT NULL UNIQUE CHECK (iata_code ~ '^[A-Z]{3}$'),
  name VARCHAR(255) NOT NULL,
  city VARCHAR(150) NOT NULL,
  country VARCHAR(150) NOT NULL,
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parking_stands (
  id UUID PRIMARY KEY,
  airport_id UUID NOT NULL REFERENCES airports(id) ON DELETE RESTRICT,
  code VARCHAR(30) NOT NULL,
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (airport_id,code)
);
CREATE INDEX ix_parking_stands_airport ON parking_stands(airport_id);

CREATE TABLE aircraft_types (
  id UUID PRIMARY KEY,
  code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(80) NOT NULL,
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE aircraft (
  id UUID PRIMARY KEY,
  tail_number VARCHAR(30) NOT NULL UNIQUE,
  aircraft_type_id UUID NOT NULL REFERENCES aircraft_types(id) ON DELETE RESTRICT,
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_aircraft_type ON aircraft(aircraft_type_id);

CREATE TABLE operation_types (
  id UUID PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Это изменяемые БАЗОВЫЕ ориентиры времени для создания новых черновиков.
-- Не редактируют опубликованный шаблон: в procedure_stages хранится его снимок.
CREATE TABLE aircraft_type_operation_norms (
  id UUID PRIMARY KEY,
  aircraft_type_id UUID NOT NULL REFERENCES aircraft_types(id) ON DELETE RESTRICT,
  operation_type_id UUID NOT NULL REFERENCES operation_types(id) ON DELETE RESTRICT,
  duration_seconds INTEGER NOT NULL CHECK (duration_seconds > 0),
  description TEXT,
  revision BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (aircraft_type_id,operation_type_id)
);
CREATE INDEX ix_norms_type ON aircraft_type_operation_norms(aircraft_type_id);

-- Перенос утверждённых записей старой универсальной модели, ID остаются прежними.
INSERT INTO aircraft_types(id,code,name,category,description,status)
SELECT r.logical_id, r.code, r.name, COALESCE(r.data->>'category','UNKNOWN'),r.description,
       CASE WHEN r.data->>'status' = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM directory_records r JOIN reference_versions v ON v.id=r.version_id
JOIN directories d ON d.id=v.directory_id
WHERE d.kind='AIRCRAFT_TYPE' AND v.status='APPROVED';

INSERT INTO aircraft(id,tail_number,aircraft_type_id,description,status)
SELECT r.logical_id, COALESCE(r.data->>'tail_number',r.code),t.id,r.description,
       CASE WHEN r.data->>'status' = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM directory_records r JOIN reference_versions v ON v.id=r.version_id
JOIN directories d ON d.id=v.directory_id
JOIN aircraft_types t ON t.code=r.data->>'aircraft_type_code'
WHERE d.kind='AIRCRAFT' AND v.status='APPROVED';

INSERT INTO operation_types(id,code,name,description,status)
SELECT r.logical_id,r.code,r.name,r.description,
       CASE WHEN r.data->>'status' = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM directory_records r JOIN reference_versions v ON v.id=r.version_id
JOIN directories d ON d.id=v.directory_id
WHERE d.kind='OPERATION_TYPE' AND v.status='APPROVED';

-- Все коды ниже являются учебными примерами; стоянки вымышлены.
INSERT INTO airports(id,iata_code,name,city,country,description) VALUES
('11111111-1111-4111-8111-111111111111','SVO','Шереметьево (демо)','Москва','Россия','Учебная карточка аэропорта'),
('22222222-2222-4222-8222-222222222222','DME','Домодедово (демо)','Домодедово','Россия','Учебная карточка аэропорта');
INSERT INTO parking_stands(id,airport_id,code,description) VALUES
('33333333-3333-4333-8333-333333333331','11111111-1111-4111-8111-111111111111','DEMO-101','Вымышленная учебная стоянка'),
('33333333-3333-4333-8333-333333333332','11111111-1111-4111-8111-111111111111','DEMO-102','Вымышленная учебная стоянка'),
('33333333-3333-4333-8333-333333333333','22222222-2222-4222-8222-222222222222','DEMO-201','Вымышленная учебная стоянка');

-- Учебные нормативы зависят от пары (тип ВС, операция), а не только от операции.
-- Для A320 базовые значения взяты из старой APPROVED-версии, для B738 изменены условно.
INSERT INTO aircraft_type_operation_norms(id,aircraft_type_id,operation_type_id,duration_seconds,description)
SELECT gen_random_uuid(),t.id,o.id,
       CASE WHEN t.code='B738' THEN
           CASE o.code WHEN 'INSPECT' THEN 1080 WHEN 'REFUEL' THEN 1500
                       WHEN 'BOARDING' THEN 2100 WHEN 'PUSHBACK' THEN 720 ELSE 1200 END
           ELSE COALESCE((r.data->>'default_duration_seconds')::integer,1200) END,
       'Учебное значение, не для реальных работ'
FROM aircraft_types t CROSS JOIN operation_types o
LEFT JOIN directory_records r ON r.logical_id=o.id AND r.version_id IN
   (SELECT v.id FROM reference_versions v JOIN directories d ON d.id=v.directory_id
    WHERE d.kind='OPERATION_TYPE' AND v.status='APPROVED')
WHERE t.code IN ('A320','B738') AND o.code IN ('INSPECT','REFUEL','BOARDING','PUSHBACK');
