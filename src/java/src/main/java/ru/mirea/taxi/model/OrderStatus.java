package ru.mirea.taxi.model;

public enum OrderStatus {
    CREATED("Создан"),
    HOLD_FUNDS("Средства заблокированы (Hold)"),
    SEARCHING("Поиск и ранжирование водителей"),
    ASSIGNED("Водитель назначен"),
    ARRIVED("Водитель прибыл на место"),
    IN_PROGRESS("Поездка выполняется"),
    COMPLETED("Поездка завершена (Сплитование средств)"),
    CANCELLED("Заказ отменен");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
