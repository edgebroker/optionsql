-- Insert all strikes into top_strikes_ticker_expiration
INSERT INTO top_strikes_ticker_expiration (
    ticker_symbol,
    expiration_date,
    strike_price,
    net_gex,
    net_dex,
    open_interest,
    volume,
    mm_hedge_behaviour
)
SELECT
    ticker_symbol,
    expiration_date,
    strike_price,
    call_gex - put_gex AS net_gex,
    call_dex + put_dex AS net_dex,
    call_oi + put_oi AS open_interest,
    call_volume + put_volume AS volume,
    CASE
        WHEN call_gex - put_gex > 0 THEN 'Sell to Hedge'
        ELSE 'Buy to Hedge'
        END AS mm_hedge_behaviour
FROM
    optionchains;
