#!/bin/bash

URL="http://localhost:8080/api/v1/payments"

for i in $(seq 1 10)
do
if (( i % 2 == 0 ))
then
SOURCE=1
DEST=2
else
SOURCE=2
DEST=1
fi

AMOUNT=$(awk -v min=1 -v max=1000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}')

PAYMENT_REF=$(printf "PAY-2021-%03d" "$i")

curl -s -X POST "$URL" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $PAYMENT_REF" \
  -d "{
        \"paymentReference\":\"$PAYMENT_REF\",
        \"sourceAccountId\":$SOURCE,
        \"destinationAccountId\":$DEST,
        \"amount\":$AMOUNT
      }"

echo " Sent $PAYMENT_REF : $SOURCE -> $DEST Amount=$AMOUNT"

sleep 0.1

done

echo "Completed 100 payment requests."
