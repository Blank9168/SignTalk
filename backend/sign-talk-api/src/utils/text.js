// "Magandang Umaga!" -> "magandang_umaga", "Niño" -> "nino". Matches the
// existing dictionary slug style ([a-z0-9_]).
function slugify(value) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

module.exports = { slugify, escapeRegex };
