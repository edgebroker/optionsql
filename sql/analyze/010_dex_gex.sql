UPDATE optionchains AS o
SET
    call_dex = call_delta * call_oi * 100,
    put_dex = put_delta * put_oi * 100,
    call_gex = call_gamma * call_oi * t.current_price * 100,
    put_gex = put_gamma * put_oi * t.current_price * 100
FROM ticker AS t
WHERE o.ticker_symbol = t.ticker_symbol;
