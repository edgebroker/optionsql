SELECT
    strike_price
FROM
    optionchains oc
WHERE
    ticker_symbol = ?
  AND expiration_date = ?
  AND put_delta BETWEEN -0.25 AND -0.10
ORDER BY strike_price;
