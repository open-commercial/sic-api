ALTER TABLE sucursal MODIFY lema varchar(255) DEFAULT null;
ALTER TABLE sucursal MODIFY telefono varchar(255) DEFAULT null;
ALTER TABLE proveedor MODIFY telPrimario varchar(255) DEFAULT null;
ALTER TABLE proveedor MODIFY telSecundario varchar(255) DEFAULT null;
ALTER TABLE proveedor MODIFY contacto varchar(255) DEFAULT null;
ALTER TABLE proveedor MODIFY web varchar(255) DEFAULT null;
ALTER TABLE proveedor MODIFY email varchar(255) DEFAULT null;
ALTER TABLE producto MODIFY nota varchar(255) DEFAULT null;
ALTER TABLE factura MODIFY observaciones varchar(255) DEFAULT null;