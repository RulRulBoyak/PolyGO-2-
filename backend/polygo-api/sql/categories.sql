CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    icon_res VARCHAR(50) DEFAULT 'ic_category_default',
    is_published BOOLEAN DEFAULT TRUE,
    proposed_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (proposed_by) REFERENCES users(id)
);

INSERT IGNORE INTO categories (name, icon_res) VALUES
('Food', 'ic_category_food'),
('Drink', 'ic_category_drink'),
('Tech', 'ic_category_tech'),
('Electronics', 'ic_category_electronics'),
('Fashion', 'ic_category_fashion'),
('Books', 'ic_category_books'),
('Repair', 'ic_category_repair'),
('Home', 'ic_category_home'),
('Laundry', 'ic_category_laundry'),
('Delivery', 'ic_category_delivery'),
('Services', 'ic_category_services');