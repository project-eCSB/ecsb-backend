alter table game_session add foreign key (created_by) references login_user (id);
