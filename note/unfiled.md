`StmtTycker`

`visitFn` visits a function declaration
- Check the current telescope of the function and the result type are well-typed.
- Build a signature.
- Check the definition body.
  - If it is defined by direct definition, zonk the body.
  - If it is defined by pattern matching...
    - Elaborate every clause and filter out absurd ones.
    - Check for pattern confluence.
- Return new definition with the checked body and signature
  - Note: currently `ensureConfluent` should be called after constructing the to-be-returned `FnDef`.
