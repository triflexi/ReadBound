export async function handleSelection(context) {
  const word = encodeURIComponent(context.quote.trim());
  return {
    type: "http",
    method: "GET",
    url: `https://api.dictionaryapi.dev/api/v2/entries/en/${word}`
  };
}
