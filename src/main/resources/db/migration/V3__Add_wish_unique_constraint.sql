alter table wish add constraint uk_wish_member_product unique (member_id, product_id);
