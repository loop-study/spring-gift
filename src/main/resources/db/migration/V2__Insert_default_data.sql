insert into category (name, color, image_url, description)
values ('전자기기', '#1E90FF', 'https://example.com/images/electronics.jpg', '스마트폰, 노트북 등 전자기기');
insert into category (name, color, image_url, description)
values ('패션', '#FF6347', 'https://example.com/images/fashion.jpg', '의류, 신발, 액세서리');
insert into category (name, color, image_url, description)
values ('식품', '#32CD32', 'https://example.com/images/food.jpg', '신선식품, 가공식품, 음료');

insert into product (name, price, image_url, category_id)
values ('맥북 프로 16인치', 3360000, 'https://example.com/images/macbook.jpg', 1);
insert into product (name, price, image_url, category_id)
values ('아이폰 16', 1350000, 'https://example.com/images/iphone.jpg', 1);
insert into product (name, price, image_url, category_id)
values ('나이키 에어맥스', 179000, 'https://example.com/images/airmax.jpg', 2);
insert into product (name, price, image_url, category_id)
values ('레비스 청바지', 89000, 'https://example.com/images/jeans.jpg', 2);
insert into product (name, price, image_url, category_id)
values ('제주 감귤 5kg', 25000, 'https://example.com/images/tangerine.jpg', 3);
insert into product (name, price, image_url, category_id)
values ('한우 등심 1kg', 65000, 'https://example.com/images/beef.jpg', 3);

insert into member (email, password, point)
values ('admin@example.com', '$2a$10$hIzc4RJqmfIG1t4MWvkMzujzJlZEJEl.FptaBV9Cysj9BHt7nc4KW', 10000000);
insert into member (email, password, point)
values ('user1@example.com', '$2a$10$JvlEy17wgvYaLLp0Y45a/./UYSwFgwXlr7EuERtxaoEgB5JVgpvdO', 5000000);
insert into member (email, password, point)
values ('user2@example.com', '$2a$10$6lyN3B.q8dD8FpjQegHU7uT7Mkxbfb6HN0s0thxZcg3H7EmDBw85S', 3000000);

insert into wish (member_id, product_id)
values (2, 1);
insert into wish (member_id, product_id)
values (2, 3);
insert into wish (member_id, product_id)
values (3, 2);
insert into wish (member_id, product_id)
values (3, 5);

insert into options (product_id, name, quantity)
values (1, '스페이스 블랙 / M1 Pro', 10);
insert into options (product_id, name, quantity)
values (1, '실버 / M1 Max', 5);
insert into options (product_id, name, quantity)
values (2, '블루 / 256GB', 30);
insert into options (product_id, name, quantity)
values (2, '블랙 / 512GB', 20);
insert into options (product_id, name, quantity)
values (3, '270mm', 15);
insert into options (product_id, name, quantity)
values (4, '32인치', 25);
insert into options (product_id, name, quantity)
values (5, '일반 감귤', 50);
insert into options (product_id, name, quantity)
values (6, '1++ 등급', 8);

insert into orders (option_id, member_id, quantity, message, order_date_time)
values (3, 2, 1, '생일 축하해! 🎉', '2026-02-10 14:30:00');
insert into orders (option_id, member_id, quantity, message, order_date_time)
values (5, 2, 2, null, '2026-02-12 09:15:00');
insert into orders (option_id, member_id, quantity, message, order_date_time)
values (7, 3, 1, '엄마 감사합니다 ❤️', '2026-02-14 18:00:00');
insert into orders (option_id, member_id, quantity, message, order_date_time)
values (8, 3, 1, '맛있게 드세요!', '2026-02-15 11:45:00');
