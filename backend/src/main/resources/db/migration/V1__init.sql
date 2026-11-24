CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE clients (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    company VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(20),
    address TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    skills TEXT,
    experience INTEGER,
    status VARCHAR(20) DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vendors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    company VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(20),
    services TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    client_id BIGINT REFERENCES clients(id),
    manager_id BIGINT REFERENCES users(id),
    start_date DATE,
    deadline DATE,
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    status VARCHAR(20) DEFAULT 'PLANNED',
    progress INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES projects(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_date DATE,
    deadline DATE,
    estimated_duration INTEGER,
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    status VARCHAR(20) DEFAULT 'TODO',
    progress INTEGER DEFAULT 0,
    assigned_worker_id BIGINT REFERENCES workers(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed Initial Admin User (password is 'admin' hashed with BCrypt)
INSERT INTO users (username, password, email, role) VALUES ('admin', '$2a$10$7oU44O51dT9l4B6BBZk8k.AEb0Trf1gNwZhxXJ0zEwFQ8rN9orfiK', 'admin@example.com', 'ADMIN');
INSERT INTO users (username, password, email, role) VALUES ('manager', '$2a$10$7oU44O51dT9l4B6BBZk8k.AEb0Trf1gNwZhxXJ0zEwFQ8rN9orfiK', 'manager@example.com', 'PROJECT_MANAGER');
INSERT INTO users (username, password, email, role) VALUES ('worker', '$2a$10$7oU44O51dT9l4B6BBZk8k.AEb0Trf1gNwZhxXJ0zEwFQ8rN9orfiK', 'worker@example.com', 'WORKER');
INSERT INTO users (username, password, email, role) VALUES ('client', '$2a$10$7oU44O51dT9l4B6BBZk8k.AEb0Trf1gNwZhxXJ0zEwFQ8rN9orfiK', 'client@example.com', 'CLIENT');
INSERT INTO users (username, password, email, role) VALUES ('vendor', '$2a$10$7oU44O51dT9l4B6BBZk8k.AEb0Trf1gNwZhxXJ0zEwFQ8rN9orfiK', 'vendor@example.com', 'VENDOR');
