create table utxoMixData (id identity not null, hash binary(32) not null, poolId varchar(32), mixesDone integer not null default 0, forwarding bigint, wallet bigint not null);
