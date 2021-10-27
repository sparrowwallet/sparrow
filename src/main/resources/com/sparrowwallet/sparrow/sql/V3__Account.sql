alter table wallet add column label varchar(255) after name;
alter table mixConfig add column indexRange varchar(10) after mixOnStartup;