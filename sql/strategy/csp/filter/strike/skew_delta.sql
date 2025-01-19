SELECT
    CASE
        WHEN oc.skew_delta BETWEEN -0.10 AND at.balanced_skew_delta THEN true
        ELSE false
        END AS passed
FROM
    optionchains oc
        JOIN
    adaptive_thresholds at
    ON oc.ticker_symbol = at.ticker_symbol
        AND oc.expiration_date = at.expiration_date
WHERE
    oc.ticker_symbol = ?
  AND oc.expiration_date = ?
  AND oc.strike_price = ?;
