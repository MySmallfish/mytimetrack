# MyTimetrack Website (Static)

This is a static marketing site for MyTimetrack.

## Local Preview
From `website/`:
```bash
python3 -m http.server 5173
```
Then open `http://localhost:5173/`.

## Configure Store Links
Edit:
- `website/assets/js/config.js`

If `iosUrl` or `androidUrl` is empty, the corresponding download button will show as "Coming soon".

