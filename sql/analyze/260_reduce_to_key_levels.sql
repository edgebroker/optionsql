-- Insert into `key_levels` table for all tickers
INSERT INTO key_levels (ticker_symbol, strike_price, max_zgs_count, max_top_count, net_gex, net_dex, mm_hedge_behaviour, open_interest, volume)
SELECT
    tse.ticker_symbol,
    tse.strike_price,
    MAX(tse.zgs_count) AS max_zgs_count,
    MAX(tse.top_count) AS max_top_count,
    SUM(tse.net_gex) AS net_gex,
    SUM(tse.net_dex) AS net_dex,
    CASE
        WHEN SUM(tse.net_gex) > 0 THEN 'Buy to Hedge'
        ELSE 'Sell to Hedge'
        END AS mm_hedge_behaviour,
    SUM(tse.open_interest) AS open_interest,
    SUM(tse.volume) AS volume
FROM
    top_strikes_ticker_expiration tse
GROUP BY
    tse.ticker_symbol,
    tse.strike_price
ON CONFLICT (ticker_symbol, strike_price)
    DO UPDATE
    SET
        max_zgs_count = EXCLUDED.max_zgs_count,
        max_top_count = EXCLUDED.max_top_count,
        net_gex = EXCLUDED.net_gex,
        net_dex = EXCLUDED.net_dex,
        mm_hedge_behaviour = EXCLUDED.mm_hedge_behaviour,
        open_interest = EXCLUDED.open_interest,
        volume = EXCLUDED.volume;
