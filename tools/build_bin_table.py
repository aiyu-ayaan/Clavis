#!/usr/bin/env python3
"""Build core/src/main/assets/bins.bin from the BIN List Data CSV (CC BY 4.0).

Source: https://github.com/venelinkochev/bin-list-data (bin-list-data.csv). Not committed: download it, then
    python3 tools/build_bin_table.py path/to/bin-list-data.csv core/src/main/assets/bins.bin

Format (big-endian), read by com.aiyu.clavis.core.card.BinLookup:
    "CBIN" u8 version=1
    u32 issuerCount, then per issuer: u8 len, UTF-8 bytes
    u16 countryCount, then 2 ASCII bytes each
    u32 recordCount, then sorted records of 8 bytes:
        u24 bin, u16 issuer (0xFFFF none), u8 type, u8 network, u8 country (0xFF none)
type: 0 unknown, 1 credit, 2 debit, 3 prepaid. network: CardNetwork ordinal (9 = unknown).
"""
import csv
import re
import struct
import sys

NETWORKS = ["VISA", "MASTERCARD", "RUPAY", "AMEX", "DISCOVER", "DINERS", "JCB", "UNIONPAY", "MAESTRO", "UNKNOWN"]
BRAND = {
    "VISA": "VISA", "MASTERCARD": "MASTERCARD", "RUPAY": "RUPAY", "JCB/RUPAY": "RUPAY",
    "AMERICAN EXPRESS": "AMEX", "DISCOVER": "DISCOVER", "DINERS CLUB INTERNATIONAL": "DINERS",
    "JCB": "JCB", "CHINA UNION PAY": "UNIONPAY", "MAESTRO": "MAESTRO",
}
ACRONYMS = {"ICICI", "IDBI", "IDFC", "SBI", "AU", "RBL", "UCO", "HSBC", "BNP", "UBS", "ING", "PNB", "TSB", "BBVA",
            "ABN", "AMRO", "BMO", "RBC", "CIBC", "DBS", "OCBC", "ANZ", "NAB", "USA", "UAE", "UK", "US", "USAA", "PNC",
            "BB&T", "TD", "SNS", "KCB", "CIMB", "RHB", "BDO", "BPI", "QNB", "NBD", "ADCB", "FAB", "BOI", "BOB"}
LOWER = {"OF", "AND", "THE", "DE", "DU", "LA", "DEL", "DA", "DO", "DI", "FOR"}
SUFFIX = re.compile(r"[,\s]+(LTD\.?|LIMITED|INC\.?|N\.A\.?|PLC|CORP\.?|CORPORATION|S\.A\.?|AG|LLC|CO\.?)$", re.I)


def clean(name: str) -> str:
    name = " ".join(name.split()).strip(" ,")
    for _ in range(3):
        name = SUFFIX.sub("", name).strip(" ,")
    words = []
    for i, w in enumerate(name.split(" ")):
        core = w.strip(",.()")
        if core.upper() in ACRONYMS or (core.isalpha() and not re.search(r"[AEIOUY]", core.upper())):
            words.append(w.upper())
        elif i > 0 and core.upper() in LOWER:
            words.append(w.lower())
        else:
            words.append(w[:1].upper() + w[1:].lower())
    return " ".join(words)[:60]


def card_type(row) -> int:
    if "PREPAID" in row["Category"].upper():
        return 3
    t = row["Type"].upper()
    return 1 if t in ("CREDIT", "CHARGE CARD") else 2 if t == "DEBIT" else 0


def main(src, dst):
    issuers, countries, records = {}, {}, {}
    with open(src, encoding="utf-8", errors="replace") as f:
        for row in csv.DictReader(f):
            b = row["BIN"].strip()
            if len(b) != 6 or not b.isdigit():
                continue
            name = clean(row["Issuer"]) if row["Issuer"].strip() else ""
            iid = issuers.setdefault(name, len(issuers)) if name else 0xFFFF
            cc = row["isoCode2"].strip().upper()
            cid = countries.setdefault(cc, len(countries)) if len(cc) == 2 else 0xFF
            net = NETWORKS.index(BRAND.get(row["Brand"].strip().upper(), "UNKNOWN"))
            records[int(b)] = (iid, card_type(row), net, cid)
    assert len(issuers) < 0xFFFF and len(countries) < 0xFF
    out = bytearray(b"CBIN") + bytes([1])
    out += struct.pack(">I", len(issuers))
    for name in sorted(issuers, key=issuers.get):
        raw = name.encode("utf-8")[:255]
        out += bytes([len(raw)]) + raw
    out += struct.pack(">H", len(countries))
    for cc in sorted(countries, key=countries.get):
        out += cc.encode("ascii")
    out += struct.pack(">I", len(records))
    for b in sorted(records):
        iid, t, net, cid = records[b]
        out += b.to_bytes(3, "big") + struct.pack(">HBBB", iid, t, net, cid)
    with open(dst, "wb") as f:
        f.write(out)
    print(f"{len(records)} BINs, {len(issuers)} issuers, {len(countries)} countries, {len(out)} bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
