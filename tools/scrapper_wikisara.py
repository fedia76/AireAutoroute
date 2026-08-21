#!/usr/bin/env python3
"""
Scrape WikiSara : pour chaque autoroute française, récupère la longueur
(page principale) et le détail des aires - nom, sens, PK - (page "(Aires)").

Pas de retry automatique : chaque page n'est tentée qu'une fois. En cas de
blocage anti-bot (403/429), le statut est simplement enregistré comme échec.

Usage :
    pip install requests beautifulsoup4

    python scrape_wikisara_aires.py

Résultats :
    - wikisara_urls_status.csv   : un résumé par autoroute (accessibilité + longueur)
    - wikisara_aires.csv         : le détail de toutes les aires trouvées
                                    (autoroute, sens, nom, type, pk)
"""

import csv
import os
import re
import time
import urllib.parse
import requests
from bs4 import BeautifulSoup

BASE_URL = "https://routes.fandom.com/wiki/"

# Dossier de sortie des CSV. Sur Android/Pydroid, le répertoire de travail par
# défaut n'est pas toujours visible dans le gestionnaire de fichiers : on écrit
# donc explicitement dans le dossier Téléchargements partagé quand il existe.
_ANDROID_DOWNLOADS = "/storage/emulated/0/Download"
if os.path.isdir(_ANDROID_DOWNLOADS):
    OUTPUT_DIR = _ANDROID_DOWNLOADS
else:
    OUTPUT_DIR = os.getcwd()

AUTOROUTES = [
    "A1", "A1_(972)", "A1a", "A2", "A3", "A4", "A5", "A6", "A6a", "A6b",
    "A7", "A8", "A9", "A10", "A11", "A11.1", "A12", "A13", "A13a", "A14",
    "A15", "A16", "A19", "A20", "A21", "A22", "A23", "A25", "A26", "A27",
    "A28", "A29", "A30", "A31", "A33", "A34", "A35", "A36", "A38", "A39",
    "A40", "A41", "A42", "A43", "A46", "A47", "A48", "A49", "A50", "A51",
    "A52", "A54", "A55", "A57", "A61", "A62", "A63", "A64", "A65", "A66",
    "A68", "A69", "A71", "A72", "A75", "A77", "A79", "A81", "A82", "A83",
    "A84", "A85", "A86", "A87", "A88", "A89", "A103", "A104", "A105",
    "A106", "A115", "A126", "A131", "A132", "A139", "A140", "A150",
    "A151", "A154", "A170", "A211", "A216", "A304", "A311", "A313",
    "A314", "A315", "A320", "A330", "A344", "A352", "A355", "A391",
    "A404", "A406", "A410", "A411", "A430", "A432", "A450", "A466",
    "A480", "A500", "A501", "A502", "A507", "A515", "A517", "A520",
    "A551", "A552", "A557", "A570", "A620", "A621", "A623", "A624",
    "A630", "A641", "A645", "A660", "A701", "A709", "A710", "A711",
    "A712", "A714", "A719", "A750", "A811", "A813", "A837", "A844",
]

assert len(AUTOROUTES) == 143, f"Attention : {len(AUTOROUTES)} entrées trouvées, 143 attendues."

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "fr-FR,fr;q=0.9",
}

TIMEOUT = 15
DELAY_BETWEEN_REQUESTS = 0.5

LENGTH_RE = re.compile(
    r'data-source="Longueur".*?pi-data-value[^"]*">\s*([^<]+?)\s*</div>',
    re.DOTALL,
)

# Pour extraire un nombre depuis "Km 5" / "Km 122" / etc.
PK_NUM_RE = re.compile(r'(\d+(?:[.,]\d+)?)')


def build_urls(slug: str):
    main_slug = f"Autoroute_française_{slug}"
    aires_slug = f"Autoroute_française_{slug}_(Aires)"
    main_url = BASE_URL + urllib.parse.quote(main_slug)
    aires_url = BASE_URL + urllib.parse.quote(aires_slug)
    return main_url, aires_url


def _single_attempt(session: requests.Session, url: str):
    try:
        resp = session.get(url, headers=HEADERS, timeout=TIMEOUT, allow_redirects=True)
        code = resp.status_code
        if code == 200:
            return "OK", code, resp.text
        elif code == 404:
            return "INTROUVABLE (404)", code, None
        elif code in (403, 429):
            return "BLOQUÉ (anti-bot / rate-limit)", code, None
        else:
            return "CODE INATTENDU", code, None
    except requests.exceptions.Timeout:
        return "TIMEOUT", None, None
    except requests.exceptions.RequestException as e:
        return f"ERREUR ({type(e).__name__})", None, None


def check_url(session: requests.Session, url: str, label: str = ""):
    """Une seule tentative, pas de retry automatique."""
    status, code, html = _single_attempt(session, url)
    return status, code, html


def extract_length(html: str):
    if not html:
        return None
    match = LENGTH_RE.search(html)
    if match:
        return match.group(1).replace("\xa0", " ").strip()
    return None


def extract_aires(html: str, autoroute_slug: str):
    """
    Parcourt les <h3> 'Sens X - Y' de la section 'Détail des aires' et lit
    le <table class="wikitable"> qui suit chacun. Retourne une liste de dicts.
    """
    if not html:
        return []

    soup = BeautifulSoup(html, "html.parser")
    aires = []

    for h3 in soup.find_all("h3"):
        span = h3.find("span", class_="mw-headline")
        if not span:
            continue
        heading_id = span.get("id", "") or ""
        heading_text = span.get_text(strip=True)

        # On ne garde que les titres de sens de circulation
        if not heading_id.startswith("Sens_") and not heading_text.lower().startswith("sens"):
            continue

        sens = heading_text

        table = h3.find_next("table", class_="wikitable")
        if table is None:
            continue

        rows = table.find_all("tr")
        if not rows:
            continue

        header_cells = rows[0].find_all("th")
        headers = [th.get_text(strip=True) for th in header_cells]
        if not headers:
            continue

        for tr in rows[1:]:
            cells = tr.find_all("td")
            if not cells:
                continue
            values = [td.get_text(" ", strip=True) for td in cells]
            # Alignement défensif si le nombre de cellules ne correspond pas aux headers
            row_dict = dict(zip(headers, values))

            nom_aire = row_dict.get("Aire", "").strip()
            if not nom_aire:
                continue

            pk_text = row_dict.get("Localisation", "").strip()
            pk_match = PK_NUM_RE.search(pk_text)
            pk_num = pk_match.group(1).replace(",", ".") if pk_match else ""

            aires.append({
                "autoroute": autoroute_slug,
                "sens": sens,
                "nom_aire": nom_aire,
                "type": row_dict.get("Type", "").strip(),
                "pk_texte": pk_text,
                "pk_km": pk_num,
            })

    return aires


def main():
    session = requests.Session()
    summary_results = []
    all_aires = []

    print(f"Scraping de {len(AUTOROUTES)} autoroutes (page principale + page Aires)...\n")

    for i, slug in enumerate(AUTOROUTES, start=1):
        main_url, aires_url = build_urls(slug)

        main_status, main_code, main_html = check_url(session, main_url, f"{slug} (principale)")
        length = extract_length(main_html)
        time.sleep(DELAY_BETWEEN_REQUESTS)

        aires_status, aires_code, aires_html = check_url(session, aires_url, f"{slug} (aires)")
        time.sleep(DELAY_BETWEEN_REQUESTS)

        aires_for_this_route = extract_aires(aires_html, slug) if aires_status == "OK" else []
        all_aires.extend(aires_for_this_route)

        length_display = length if length else "?"
        print(f"[{i:3}/{len(AUTOROUTES)}] {slug:12} | Longueur: {length_display:10} | "
              f"Principale: {main_status:30} | Aires: {aires_status:30} | "
              f"{len(aires_for_this_route)} aires trouvées")

        # Affiche le détail de chaque aire trouvée, pour vérification immédiate
        for aire in aires_for_this_route:
            print(f"        - {aire['nom_aire']:30} | {aire['sens']:20} | "
                  f"{aire['type']:8} | PK {aire['pk_texte']}")

        summary_results.append({
            "autoroute": slug,
            "longueur": length or "",
            "url_principale": main_url,
            "statut_principale": main_status,
            "code_http_principale": main_code,
            "url_aires": aires_url,
            "statut_aires": aires_status,
            "code_http_aires": aires_code,
            "nb_aires_trouvees": len(aires_for_this_route),
        })

    # --- Export CSV résumé ---
    summary_file = os.path.join(OUTPUT_DIR, "wikisara_urls_status.csv")
    with open(summary_file, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(summary_results[0].keys()), delimiter=";")
        writer.writeheader()
        writer.writerows(summary_results)

    # --- Export CSV détail des aires ---
    aires_file = os.path.join(OUTPUT_DIR, "wikisara_aires.csv")
    if all_aires:
        with open(aires_file, "w", newline="", encoding="utf-8-sig") as f:
            writer = csv.DictWriter(f, fieldnames=list(all_aires[0].keys()), delimiter=";")
            writer.writeheader()
            writer.writerows(all_aires)
    else:
        print("Aucune aire extraite, fichier aires non créé.")

    # --- Résumé ---
    nb_ok = sum(1 for r in summary_results if r["statut_aires"] == "OK")
    nb_ko = len(summary_results) - nb_ok
    print(f"\nRésumé : {nb_ok} pages 'Aires' accessibles / {nb_ko} en erreur ou introuvables.")
    print(f"Total d'aires extraites : {len(all_aires)}")
    print(f"\n>>> Fichiers écrits dans : {OUTPUT_DIR}")
    print(f">>> Détail des URLs : {summary_file}")
    print(f">>> Détail des aires : {aires_file}")

    # --- Récapitulatif complet en console, pour vérification sans avoir à ---
    # --- rouvrir le CSV (utile notamment sur mobile) ---
    if all_aires:
        print("\n" + "=" * 70)
        print(f"RÉCAPITULATIF COMPLET DES {len(all_aires)} AIRES EXTRAITES")
        print("=" * 70)
        current_autoroute = None
        for aire in all_aires:
            if aire["autoroute"] != current_autoroute:
                current_autoroute = aire["autoroute"]
                print(f"\n--- {current_autoroute} ---")
            print(f"  {aire['nom_aire']:30} | {aire['sens']:20} | "
                  f"{aire['type']:8} | PK {aire['pk_texte']}")
        print("\n" + "=" * 70)

    failures = [r for r in summary_results if r["statut_aires"] != "OK"]
    if failures:
        print("\nAutoroutes en échec définitif (avec longueur si connue) :")
        for r in failures:
            print(f"  - {r['autoroute']:12} | longueur: {r['longueur'] or '?'} | statut: {r['statut_aires']}")


if __name__ == "__main__":
    main()
