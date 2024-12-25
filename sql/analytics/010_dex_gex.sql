UPDATE optionchains AS o
SET
    dex = delta * oi * 100,
    gex = gamma * oi * t.current_price * 100
FROM ticker AS t
WHERE o.ticker_symbol = t.ticker_symbol;
