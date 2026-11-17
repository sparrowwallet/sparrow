create table externalOutputLabel (txid binary(32) not null, index bigint not null, label varchar(255) not null, primary key (txid, index));
create table fiatRate (txid binary(32) not null, currency varchar(3) not null, rate varchar(40) not null, source integer not null, primary key (txid, currency));
alter table blockTransaction alter column transaction set data type varbinary(4000000);