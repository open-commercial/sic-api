SET GLOBAL foreign_key_checks = 0;
SET UNIQUE_CHECKS = 0; 
SET SQL_SAFE_UPDATES = 0;
drop table ajustecuentacorriente;
drop table pago;
ALTER TABLE factura DROP COLUMN factura.pagada;
ALTER TABLE notadebito DROP COLUMN notadebito.pagada;
ALTER TABLE recibo DROP COLUMN recibo.saldoSobrante;
ALTER TABLE rengloncuentacorriente 
DROP FOREIGN KEY `FK6daj9kxda0gxirn6k7e49uh04`;
ALTER TABLE rengloncuentacorriente
DROP INDEX `FK6daj9kxda0gxirn6k7e49uh04`;
ALTER TABLE rengloncuentacorriente DROP COLUMN rengloncuentacorriente.idAjusteCuentaCorriente;
SET SQL_SAFE_UPDATES = 1;
SET GLOBAL foreign_key_checks = 1;
SET UNIQUE_CHECKS = 1;