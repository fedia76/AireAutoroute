"""Génère les visuels de la fiche Play Store.

Géométrie unique du motif, déclinée en PNG (Store) et en vecteur (lanceur Android).

Les coordonnées sont normalisées dans [0,1]. Le PNG les multiplie par la taille de toile ;
le vecteur les projette dans la zone sûre de l'icône adaptative (les 72 unités centrales
d'un viewport de 108), pour que le masque du lanceur ne rogne rien.
"""
import math
from PIL import Image, ImageDraw, ImageFont

SORTIE = "docs/store"
FONTS = "polices"   # dossier contenant Outfit-Bold.ttf et Outfit-Regular.ttf (licence SIL OFL)

BLEU, BLANC, AMBRE = (11, 95, 165), (255, 255, 255), (255, 213, 79)
BLEU_SOMBRE, BLEU_CLAIR = (6, 52, 92), (196, 220, 242)

M = 0.165                      # marge du pictogramme
H = (1 - 2 * M) * 0.94         # hauteur utile
Y0 = M * 0.96
L = 1 - 2 * M
X0 = M


def _rect(x, y, w, h):
    return [(x, y), (x + w, y), (x + w, y + h), (x, y + h)]


def polygones_picto():
    """Le pictogramme : sol, pin, table. Liste de polygones en coordonnées normalisées."""
    p = []
    sol_y = Y0 + H * 0.90
    p.append(_rect(X0, sol_y, L, H * 0.055))

    pin_cx = X0 + L * 0.68
    tronc = L * 0.045
    p.append(_rect(pin_cx - tronc, sol_y - H * 0.30, 2 * tronc, H * 0.30))
    for haut, demi, ht in ((0.10, 0.46, 0.30), (0.26, 0.38, 0.26), (0.42, 0.29, 0.22)):
        s = Y0 + H * haut
        b = s + H * ht
        p.append([(pin_cx, s), (pin_cx + L * demi, b), (pin_cx - L * demi, b)])

    t_cx = X0 + L * 0.245
    td = L * 0.20
    plateau = sol_y - H * 0.245
    ep = H * 0.052
    p.append(_rect(t_cx - td, plateau, 2 * td, ep))
    for s in (-1, 1):
        p.append([(t_cx + s * td * 0.42, plateau + ep), (t_cx + s * td * 0.62, plateau + ep),
                  (t_cx + s * td * 0.92, sol_y), (t_cx + s * td * 0.72, sol_y)])
    banc = sol_y - H * 0.105
    for s in (-1, 1):
        a, b = t_cx + s * td * 0.55, t_cx + s * td * 1.18
        p.append(_rect(min(a, b), banc, abs(b - a), ep * 0.85))
    return p


def polygone_etoile(cx=0.255, cy=0.245, r=0.093):
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.42
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def dessiner(d, polys, couleur, ech, dx=0, dy=0):
    for poly in polys:
        d.polygon([(dx + x * ech, dy + y * ech) for x, y in poly], fill=couleur)


# --- Icône 512 -----------------------------------------------------------------------------
def icone(chemin, taille=512, sur=4):
    T = taille * sur
    img = Image.new("RGBA", (T, T), BLEU + (255,))
    d = ImageDraw.Draw(img)
    dessiner(d, polygones_picto(), BLANC, T)
    dessiner(d, [polygone_etoile()], AMBRE, T)
    img = img.resize((taille, taille), Image.LANCZOS)
    img.save(chemin)
    return img


# --- Vecteur du lanceur --------------------------------------------------------------------
def chemin_vectoriel(polys):
    """Projette dans la zone sûre : [0,1] → [18,90] du viewport 108."""
    morceaux = []
    for poly in polys:
        pts = [(18 + x * 72, 18 + y * 72) for x, y in poly]
        t = f"M{pts[0][0]:.2f},{pts[0][1]:.2f}"
        t += "".join(f"L{x:.2f},{y:.2f}" for x, y in pts[1:])
        morceaux.append(t + "Z")
    return "".join(morceaux)


def vecteur(chemin):
    xml = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Pictogramme du panneau d'aire de repos : le sol, le pin, la table de pique-nique.
         Tracé simplifié — à la taille d'une icône, le détail des branches se referme en tache.
         Les coordonnées tiennent dans les 72 unités centrales, seule zone que le masque
         adaptatif laisse toujours visible. -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="{chemin_vectoriel(polygones_picto())}" />
    <!-- L'étoile de la notation, placée dans le ciel : la seule zone libre du pictogramme. -->
    <path
        android:fillColor="#FFD54F"
        android:pathData="{chemin_vectoriel([polygone_etoile()])}" />
</vector>
'''
    open(chemin, "w").write(xml)
    return xml


# --- Bandeau 1024 × 500 --------------------------------------------------------------------
def bandeau(chemin, sur=4):
    Lx, Hy = 1024 * sur, 500 * sur
    img = Image.new("RGB", (Lx, Hy), BLEU)
    d = ImageDraw.Draw(img)
    for y in range(Hy):
        for x in range(0, Lx, 8 * sur):
            t = min(1.0, (x / Lx) * 0.55 + (y / Hy) * 0.45)
            d.rectangle([x, y, x + 8 * sur, y + 1],
                        fill=tuple(int(BLEU[i] + (BLEU_SOMBRE[i] - BLEU[i]) * t) for i in range(3)))

    y_route = int(Hy * 0.87)
    d.rectangle([0, y_route, Lx, Hy], fill=(5, 44, 78))
    tiret, ecart = 46 * sur, 34 * sur
    y_axe = y_route + (Hy - y_route) // 2
    x = -20 * sur
    while x < Lx:
        d.rectangle([x, y_axe - 3 * sur, x + tiret, y_axe + 3 * sur], fill=(110, 140, 172))
        x += tiret + ecart

    # Le bandeau ne reprend que le pictogramme : l'étoile de l'icône ferait doublon avec la
    # rangée d'étoiles du bloc de texte, et viendrait buter contre le titre.
    ech = Hy * 0.80
    dx = int(Lx * 0.78) - 0.5 * ech
    dy = y_route / 2 - 0.459 * ech        # 0.459 = milieu vertical du pictogramme seul
    dessiner(d, polygones_picto(), BLANC, ech, dx, dy)

    marge = int(Lx * 0.075)
    colonne = int(dx + 0.12 * ech) - marge - int(Lx * 0.02)

    def ajuste(f, txt, cible, depart):
        t = depart
        while t > 8:
            p = ImageFont.truetype(f, t)
            if p.getbbox(txt)[2] - p.getbbox(txt)[0] <= cible:
                return p
            t -= 2
        return ImageFont.truetype(f, 8)

    titre, l1, l2 = "Aires d'autoroute", "Les prochaines aires de votre trajet,", "notées par les voyageurs."
    f_t = ajuste(f"{FONTS}/Outfit-Bold.ttf", titre, colonne, 92 * sur)
    f_s = ajuste(f"{FONTS}/Outfit-Regular.ttf", l1, colonne, 40 * sur)

    y_t = int(Hy * 0.345)
    y_1 = y_t + int(Hy * 0.115)
    y_2 = y_1 + int(Hy * 0.088)
    y_e = y_2 + int(Hy * 0.105)
    d.text((marge, y_t), titre, font=f_t, fill=BLANC, anchor="ls")
    d.text((marge, y_1), l1, font=f_s, fill=BLEU_CLAIR, anchor="ls")
    d.text((marge, y_2), l2, font=f_s, fill=BLEU_CLAIR, anchor="ls")
    r = 15 * sur
    for i in range(5):
        pts = polygone_etoile(0, 0, 1)
        d.polygon([(marge + r + i * int(r * 2.7) + px * r, y_e + py * r) for px, py in pts], fill=AMBRE)

    img = img.resize((1024, 500), Image.LANCZOS)
    img.save(chemin)
    return img


ic = icone(f"{SORTIE}/icone-512.png")
vecteur("app/src/main/res/drawable/ic_launcher_foreground.xml")
ba = bandeau(f"{SORTIE}/bandeau-1024x500.png")
print("icône  :", ic.size, ic.mode)
print("bandeau:", ba.size, ba.mode)
