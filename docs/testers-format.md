# Testers wall data

`app/src/main/assets/testers.json` feeds the **Acknowledgements** screen (Settings → لوحة الشكر والتقدير).
Add a person once under `testers`, then add as many `reviews` as they gave.

```jsonc
{
  "testers": [
    {
      "id": "ahmad-homsi",          // stable key; reviews point at it
      "nameEn": "Ahmad Homsi",      // required
      "nameAr": "أحمد حمصي",         // optional; the English name is shown when absent
      "roleEn": "Early beta tester", // optional line under the name
      "roleAr": "مختبر مبكّر"
    }
  ],
  "reviews": [
    {
      "testerId": "ahmad-homsi",    // must match a tester id
      "textAr": "…",                // the words, as they were said. Either or both of the two
      "textEn": null,               // texts; the one matching the app language is preferred
      "stars": 5                    // optional 1-5. Omit it and no stars are drawn
    }
  ]
}
```

Quotes are shown exactly as written, in the language they were given; nothing is translated
for you. With eight or more reviews the wall splits into two rows moving in opposite directions.
