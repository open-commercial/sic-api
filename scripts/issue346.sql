ALTER TABLE pedido
ADD idPayment varchar(255) after nroPedido;
ALTER TABLE cliente
ADD puedeComprarAPlazo bit(1) after predeterminado;