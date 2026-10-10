// Shared enums so models, validators, and controllers can't drift apart.

const ROLES = ["user", "validator", "admin"];

const LANGUAGES = ["FSL", "ASL"];
const SIGN_TYPES = ["static", "dynamic"];
const HANDS = ["one", "two"];

const VALIDATION_STATUSES = ["unverified", "for_review", "verified"];

const SUBMISSION_STATUSES = ["submitted", "for_review", "verified", "published", "rejected"];
// Submissions that still "hold" their slug -- a new submission for the same
// sign is a duplicate while one of these is open.
const OPEN_SUBMISSION_STATUSES = ["submitted", "for_review", "verified"];

// action -> { from: allowed current statuses, to: resulting status }
const SUBMISSION_TRANSITIONS = {
  start_review: { from: ["submitted"], to: "for_review" },
  verify: { from: ["for_review"], to: "verified" },
  publish: { from: ["verified"], to: "published" },
  reject: { from: ["submitted", "for_review", "verified"], to: "rejected" },
};
const REVIEW_ACTIONS = Object.keys(SUBMISSION_TRANSITIONS);

const AUDIT_ENTITY_TYPES = ["dictionary", "submission", "user", "model"];

module.exports = {
  ROLES,
  LANGUAGES,
  SIGN_TYPES,
  HANDS,
  VALIDATION_STATUSES,
  SUBMISSION_STATUSES,
  OPEN_SUBMISSION_STATUSES,
  SUBMISSION_TRANSITIONS,
  REVIEW_ACTIONS,
  AUDIT_ENTITY_TYPES,
};
