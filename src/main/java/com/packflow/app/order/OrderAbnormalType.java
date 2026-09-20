package com.packflow.app.order;

/** Cause of an abnormal order, independent of its current stock snapshot. */
public enum OrderAbnormalType {
    STOCK_SHORTAGE,
    SHORT_DELIVERY,
    UNABLE_TO_DELIVER,
    OUTBOUND_CANCELLED,
    OTHER
}
