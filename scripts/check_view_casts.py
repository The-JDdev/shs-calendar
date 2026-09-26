#!/usr/bin/env python3
"""Static view-cast checker: finds findViewById<ActualType>(R.id.x) calls whose
declared type cannot hold the widget that layout XML inflates for that id.

Catches ClassCastException-at-launch bugs (the v2.0.0 crash family) without a
device, across every activity and layout.
"""
import re, os, sys

ROOT = '/home/z/my-project/shs-calendar/app/src/main'

# widget class hierarchy (child -> parent). Only needs enough edges to decide
# is-a for the widgets this app uses.
PARENT = {
    'MaterialTextView': 'AppCompatTextView',
    'AppCompatTextView': 'TextView',
    'TextView': 'View',
    'EditText': 'TextView',
    'AppCompatEditText': 'EditText',
    'TextInputEditText': 'AppCompatEditText',
    'AutoCompleteTextView': 'EditText',
    'MaterialAutoCompleteTextView': 'AutoCompleteTextView',
    'Button': 'TextView',
    'AppCompatButton': 'Button',
    'MaterialButton': 'AppCompatButton',
    'CheckBox': 'CompoundButton',
    'AppCompatCheckBox': 'CheckBox',
    'CompoundButton': 'Button',
    'RadioButton': 'CompoundButton',
    'AppCompatRadioButton': 'RadioButton',
    'Switch': 'CompoundButton',
    'SwitchMaterial': 'Switch',
    'ImageView': 'View',
    'AppCompatImageView': 'ImageView',
    'ShapeableImageView': 'AppCompatImageView',
    'ImageButton': 'ImageView',
    'AppCompatImageButton': 'ImageButton',
    'LinearLayout': 'ViewGroup',
    'FrameLayout': 'ViewGroup',
    'RelativeLayout': 'ViewGroup',
    'CoordinatorLayout': 'ViewGroup',
    'AppBarLayout': 'LinearLayout',
    'RecyclerView': 'ViewGroup',
    'NestedScrollView': 'FrameLayout',
    'ScrollView': 'FrameLayout',
    'HorizontalScrollView': 'FrameLayout',
    'MaterialCardView': 'CardView',
    'CardView': 'FrameLayout',
    'Chip': 'AppCompatCheckBox',
    'BottomNavigationView': 'FrameLayout',
    'NavigationBarView': 'FrameLayout',
    'BottomAppBar': 'Toolbar',
    'Toolbar': 'ViewGroup',
    'GridView': 'AbsListView',
    'AbsListView': 'ViewGroup',
    'ListView': 'AbsListView',
    'ViewPager': 'ViewGroup',
    'ViewPager2': 'ViewGroup',
    'TabLayout': 'HorizontalScrollView',
    'FloatingActionButton': 'ImageView',
    'ConstraintLayout': 'ViewGroup',
    'SwipeRefreshLayout': 'ViewGroup',
    'SearchView': 'ViewGroup',
    'MaterialToolbar': 'Toolbar',
    'QiblaCompassView': 'View',
    'View': 'Object',
    'ViewGroup': 'View',
    'ViewStub': 'View',
    'ProgressBar': 'View',
    'Slider': 'View',
    'MaterialSlider': 'Slider',
}

def is_a(tag, target):
    t = tag
    while True:
        if t == target:
            return True
        if t not in PARENT or t == 'Object':
            return False
        t = PARENT[t]

def norm(tag):
    return tag.split('.')[-1]

def norm_t(t):
    return t.split('.')[-1]

# 1) collect id -> tags across all layouts
id_tags = {}
for dirpath, _, files in os.walk(os.path.join(ROOT, 'res', 'layout')):
    for f in files:
        if not f.endswith('.xml'):
            continue
        xml = open(os.path.join(dirpath, f), encoding='utf-8').read()
        for m in re.finditer(r'<([\w.]+)[^>]*?android:id="@\+id/(\w+)"', xml, re.S):
            id_tags.setdefault(m.group(2), set()).add(norm(m.group(1)))

# 2) collect casts from all kotlin files
problems = []
checked = 0
for dirpath, _, files in os.walk(os.path.join(ROOT, 'java')):
    for f in files:
        if not f.endswith('.kt'):
            continue
        path = os.path.join(dirpath, f)
        kt = open(path, encoding='utf-8').read()
        # typed casts findViewById<T>(R.id.x) / findViewById<T>(R.id.x) also
        # with android.widget.X style names
        for m in re.finditer(r'findViewById<([\w.]+)>\(R\.id\.(\w+)\)', kt):
            typ, iid = norm_t(m.group(1)), m.group(2)
            checked += 1
            tags = id_tags.get(iid)
            if not tags:
                continue  # defined elsewhere (menu, include item layouts etc.) — runtime binding, not static check target
            if not any(is_a(tag, typ) for tag in tags):
                rel = os.path.relpath(path, ROOT)
                problems.append(f'{rel}: R.id.{iid} casts to {typ} but layouts declare {sorted(tags)}')
        # findViewById(...) as Type
        for m in re.finditer(r'findViewById\(R\.id\.(\w+)\)\s+as\s+([\w.]+)', kt):
            iid, typ = m.group(1), norm_t(m.group(2))
            checked += 1
            tags = id_tags.get(iid)
            if not tags:
                continue
            if not any(is_a(tag, typ) for tag in tags):
                rel = os.path.relpath(path, ROOT)
                problems.append(f'{rel}: R.id.{iid} casts (as) to {typ} but layouts declare {sorted(tags)}')

print(f'checked {checked} casts across all layouts')
if problems:
    print(f'\n{len(problems)} PROBLEMS:')
    print('\n'.join(problems))
else:
    print('ALL CASTS OK')
