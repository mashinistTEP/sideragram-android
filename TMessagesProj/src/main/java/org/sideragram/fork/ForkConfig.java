package org.sideragram.fork;

/**
 * Настройки нашего слоя форка.
 *
 * Адрес сервера по умолчанию можно поменять прямо в приложении:
 * Настройки → Sideragram → «Адрес сервера». Он хранится на устройстве.
 */
public class ForkConfig {

    /** Адрес нашего сервера (PHP+MySQL). Без слэша на конце. */
    public static final String DEFAULT_BASE_URL = "https://sideragram.atwebpages.com";

    /** Название форка — подставляется в тексты. */
    public static final String FORK_NAME = "Sideragram";

    /** Сколько последних операций показывать на экране. */
    public static final int OPERATIONS_LIMIT = 10;
}
