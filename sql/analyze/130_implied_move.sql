UPDATE optionchains
SET
    implied_move = GREATEST(
            COALESCE(call_iv, 0) * SQRT((expiration_date::DATE - CURRENT_DATE)::DOUBLE PRECISION / 365),
            COALESCE(put_iv, 0) * SQRT((expiration_date::DATE - CURRENT_DATE)::DOUBLE PRECISION / 365)
                   );
