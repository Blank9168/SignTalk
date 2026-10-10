// express-validator chains for every write route. Each array is used as
// `router.<verb>(path, ...rules, validate, handler)`.

const { body, param, query } = require("express-validator");
const { rejectUnknownFields } = require("./validate");
const { slugify } = require("../utils/text");
const {
  ROLES,
  LANGUAGES,
  SIGN_TYPES,
  HANDS,
  SUBMISSION_STATUSES,
  REVIEW_ACTIONS,
  AUDIT_ENTITY_TYPES,
  VALIDATION_STATUSES,
} = require("../config/constants");

const lower = (v) => (typeof v === "string" ? v.toLowerCase() : v);
const upper = (v) => (typeof v === "string" ? v.toUpperCase() : v);

// Required non-empty string, trimmed, with a max length.
const requiredString = (field, max) =>
  body(field)
    .isString()
    .withMessage(`${field} is required and must be a string`)
    .bail()
    .trim()
    .notEmpty()
    .withMessage(`${field} must not be empty`)
    .isLength({ max })
    .withMessage(`${field} must be at most ${max} characters`);

// Optional string (absent or null = skip), trimmed, with a max length.
const optionalString = (field, max) =>
  body(field)
    .optional({ values: "null" })
    .isString()
    .withMessage(`${field} must be a string`)
    .bail()
    .trim()
    .isLength({ max })
    .withMessage(`${field} must be at most ${max} characters`);

// Optional but, when present, must be non-empty.
const optionalNonEmptyString = (field, max) =>
  body(field)
    .optional()
    .isString()
    .withMessage(`${field} must be a string`)
    .bail()
    .trim()
    .notEmpty()
    .withMessage(`${field} must not be empty`)
    .isLength({ max })
    .withMessage(`${field} must be at most ${max} characters`);

const enumField = (field, values, { required = false } = {}) => {
  const chain = body(field);
  return (required ? chain : chain.optional())
    .isIn(values)
    .withMessage(`${field} must be one of: ${values.join(", ")}`);
};

// ---------------------------------------------------------------- dictionary

// Fields a validator/admin may change via PATCH. slug (stable id shared with
// the app/model), archive state, and validation stamps are deliberately
// excluded -- they have dedicated endpoints.
const DICTIONARY_EDITABLE_FIELDS = [
  "label",
  "language",
  "category",
  "description",
  "emoji",
  "hasVideo",
  "hasTrainingData",
  "gloss",
  "meaningEn",
  "meaningFil",
  "synonyms",
  "signType",
  "hands",
  "source",
  "validationStatus",
];
const DICTIONARY_CREATE_FIELDS = ["slug", ...DICTIONARY_EDITABLE_FIELDS];

const dictionaryMetadataRules = [
  optionalString("gloss", 60).customSanitizer(upper),
  optionalString("meaningEn", 200),
  optionalString("meaningFil", 200),
  optionalString("source", 120),
  optionalString("emoji", 16),
  body("synonyms").optional().isArray({ max: 30 }).withMessage("synonyms must be an array of at most 30 strings"),
  body("synonyms.*")
    .isString()
    .withMessage("each synonym must be a string")
    .bail()
    .trim()
    .notEmpty()
    .withMessage("synonyms must not contain empty strings")
    .isLength({ max: 60 })
    .customSanitizer(lower),
  enumField("signType", SIGN_TYPES),
  enumField("hands", HANDS),
  body("hasVideo").optional().isBoolean({ strict: true }).withMessage("hasVideo must be true or false"),
  body("hasTrainingData").optional().isBoolean({ strict: true }).withMessage("hasTrainingData must be true or false"),
];

const createDictionaryEntryRules = [
  rejectUnknownFields(DICTIONARY_CREATE_FIELDS),
  body("slug")
    .isString()
    .bail()
    .trim()
    .notEmpty()
    .matches(/^[a-z0-9_]+$/)
    .withMessage("slug must be lowercase letters, digits, and underscores (e.g. magandang_umaga)"),
  body("label").isString().trim().notEmpty(),
  body("language").isIn(LANGUAGES),
  body("category").isString().trim().notEmpty(),
  body("description").isString().trim().notEmpty(),
  enumField("validationStatus", VALIDATION_STATUSES),
  ...dictionaryMetadataRules,
];

const updateDictionaryEntryRules = [
  param("slug").isString().trim().notEmpty(),
  rejectUnknownFields(DICTIONARY_EDITABLE_FIELDS, { requireNonEmpty: true }),
  optionalNonEmptyString("label", 120),
  enumField("language", LANGUAGES),
  optionalNonEmptyString("category", 80),
  optionalNonEmptyString("description", 1000),
  body("validationStatus")
    .optional()
    .isIn(["unverified", "for_review"])
    .withMessage(
      "validationStatus can only be set to 'unverified' or 'for_review' here -- use POST /api/dictionary/:slug/verify to verify an entry"
    ),
  ...dictionaryMetadataRules,
];

// Archive/restore/verify take no body.
const dictionaryActionRules = [param("slug").isString().trim().notEmpty(), rejectUnknownFields([])];

// --------------------------------------------------------------- submissions

const SUBMISSION_FIELDS = [
  "name",
  "language",
  "gloss",
  "meaningEn",
  "meaningFil",
  "category",
  "description",
  "signType",
  "hands",
  "source",
  "mediaUrl",
  "notes",
  "submitterName",
];

const createSubmissionRules = [
  rejectUnknownFields(SUBMISSION_FIELDS, { requireNonEmpty: true }),
  requiredString("name", 80)
    .custom((v) => slugify(v).length > 0)
    .withMessage("name must contain at least one letter or digit"),
  requiredString("meaningEn", 200),
  requiredString("meaningFil", 200),
  requiredString("category", 80),
  enumField("signType", SIGN_TYPES, { required: true }),
  enumField("hands", HANDS, { required: true }),
  requiredString("source", 120),
  enumField("language", LANGUAGES),
  optionalString("gloss", 60).customSanitizer(upper),
  optionalString("description", 1000),
  optionalString("notes", 1000),
  optionalString("submitterName", 80),
  body("mediaUrl")
    .optional({ values: "falsy" })
    .isString()
    .withMessage("mediaUrl must be a string")
    .bail()
    .trim()
    .isLength({ max: 500 })
    .isURL({ protocols: ["http", "https"], require_protocol: true })
    .withMessage("mediaUrl must be an http(s) URL"),
];

const submissionIdRule = param("id").isMongoId().withMessage("Invalid submission id");

const listSubmissionsRules = [
  query("status").optional().isIn(SUBMISSION_STATUSES).withMessage(`status must be one of: ${SUBMISSION_STATUSES.join(", ")}`),
  query("limit").optional().isInt({ min: 1, max: 200 }).toInt(),
  query("page").optional().isInt({ min: 1 }).toInt(),
];

const reviewSubmissionRules = [
  submissionIdRule,
  rejectUnknownFields(["action", "note"], { requireNonEmpty: true }),
  body("action").isIn(REVIEW_ACTIONS).withMessage(`action must be one of: ${REVIEW_ACTIONS.join(", ")}`),
  optionalString("note", 1000),
];

// --------------------------------------------------------------------- users

const updateUserRoleRules = [
  param("id").isString().trim().notEmpty(),
  rejectUnknownFields(["role"], { requireNonEmpty: true }),
  body("role").isIn(ROLES).withMessage(`role must be one of: ${ROLES.join(", ")}`),
];

const updateMeRules = [rejectUnknownFields(["displayName"], { requireNonEmpty: true }), optionalString("displayName", 80)];

// --------------------------------------------------------------------- audit

const listAuditRules = [
  query("entityType").optional().isIn(AUDIT_ENTITY_TYPES),
  query("entityId").optional().isString().trim().notEmpty(),
  query("actor").optional().isString().trim().notEmpty(),
  query("action").optional().isString().trim().notEmpty(),
  query("limit").optional().isInt({ min: 1, max: 200 }).toInt(),
  query("page").optional().isInt({ min: 1 }).toInt(),
];

module.exports = {
  DICTIONARY_EDITABLE_FIELDS,
  SUBMISSION_FIELDS,
  createDictionaryEntryRules,
  updateDictionaryEntryRules,
  dictionaryActionRules,
  createSubmissionRules,
  submissionIdRule,
  listSubmissionsRules,
  reviewSubmissionRules,
  updateUserRoleRules,
  updateMeRules,
  listAuditRules,
};
