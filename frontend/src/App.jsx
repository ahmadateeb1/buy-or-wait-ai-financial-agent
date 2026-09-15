import {useMemo, useState} from 'react'

const incomeTypes = [
  'SALARIED',
  'BUSINESS',
  'FREELANCE',
  'VARIABLE',
  'STUDENT',
  'MULTIPLE',
]

const expenseCategories = [
  'HOUSING',
  'GROCERIES',
  'UTILITIES',
  'INTERNET',
  'PHONE',
  'TRANSPORTATION',
  'INSURANCE',
  'FAMILY_SUPPORT',
  'HEALTHCARE',
  'EDUCATION',
  'SUBSCRIPTIONS',
  'OTHER',
]

const usageFrequencies = [
  'DAILY',
  'FEW_TIMES_A_WEEK',
  'WEEKLY',
  'OCCASIONALLY',
  'RARELY',
]

const currencies = {INR: '₹', USD: '$', EUR: '€', GBP: '£'}

const steps = [
  {number: 1, title: 'Purchase', description: 'What are you considering?'},
  {number: 2, title: 'Income', description: 'How does money come in?'},
  {number: 3, title: 'Expenses', description: 'What does a normal month cost?'},
  {number: 4, title: 'Final context', description: 'Savings, debt and usefulness.'},
]

const initialForm = {
  incomeType: 'SALARIED',
  monthlyIncome: '',
  liquidSavings: '',
  monthlyDebtPayments: '',
  productName: '',
  price: '',
  currency: 'INR',
  alreadyOwnSimilarProduct: false,
  usageFrequency: 'DAILY',
  urgency: 5,
}

function displayLabel(value) {
  return value
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')
}

function numberOrZero(value) {
  return value === '' ? 0 : Number(value)
}

function urgencyLabel(value) {
  if (value <= 2) return 'Not urgent'
  if (value <= 4) return 'Can wait'
  if (value <= 6) return 'Moderate'
  if (value <= 8) return 'Important'
  return 'Urgent'
}

function FieldError({id, children}) {
  if (!children) return null

  return (
    <span className="field-error" id={id} role="alert">
      {children}
    </span>
  )
}

export function App() {
  const [step, setStep] = useState(1)
  const [furthestStep, setFurthestStep] = useState(1)
  const [form, setForm] = useState(initialForm)
  const [expenses, setExpenses] = useState([])
  const [nextExpenseId, setNextExpenseId] = useState(1)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitError, setSubmitError] = useState('')
  const [response, setResponse] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const totalExpenses = useMemo(
      () => expenses.reduce(
          (total, expense) => total + numberOrZero(expense.amount),
          0,
      ),
      [expenses],
  )

  const usedCategories = useMemo(
      () => new Set(expenses.map((expense) => expense.category)),
      [expenses],
  )

  const allCategoriesUsed = usedCategories.size === expenseCategories.length
  const currencySymbol = currencies[form.currency] ?? form.currency
  const currentUrgency = Number(form.urgency)

  function clearFieldError(name) {
    setFieldErrors((current) => {
      if (!current[name]) return current
      const next = {...current}
      delete next[name]
      return next
    })
  }

  function updateForm(event) {
    const {name, value} = event.target
    setForm((current) => ({...current, [name]: value}))
    clearFieldError(name)
    setSubmitError('')
    setResponse(null)
  }

  function setChoice(name, value) {
    setForm((current) => ({...current, [name]: value}))
    clearFieldError(name)
    setSubmitError('')
    setResponse(null)
  }

  function addExpense() {
    const category = expenseCategories.find(
        (candidate) => !usedCategories.has(candidate),
    )

    if (!category) return

    setExpenses((current) => [
      ...current,
      {id: nextExpenseId, category, amount: ''},
    ])
    setNextExpenseId((current) => current + 1)
    setSubmitError('')
    setResponse(null)
  }

  function updateExpense(id, field, value) {
    setExpenses((current) => current.map((expense) =>
      expense.id === id ? {...expense, [field]: value} : expense,
    ))
    clearFieldError(`expense-${id}`)
    setSubmitError('')
    setResponse(null)
  }

  function removeExpense(id) {
    setExpenses((current) => current.filter((expense) => expense.id !== id))
    clearFieldError(`expense-${id}`)
    setSubmitError('')
    setResponse(null)
  }

  function categoriesFor(expense) {
    return expenseCategories.filter(
        (category) => category === expense.category || !usedCategories.has(category),
    )
  }

  function validateStep(stepNumber) {
    const errors = {}

    if (stepNumber === 1) {
      if (!form.productName.trim()) {
        errors.productName = 'Enter the product you are considering.'
      }

      const price = Number(form.price)
      if (form.price === '' || !Number.isFinite(price) || price <= 0) {
        errors.price = 'Enter a price greater than zero.'
      }
    }

    if (stepNumber === 2) {
      const income = Number(form.monthlyIncome)
      if (!Number.isFinite(income) || income < 0) {
        errors.monthlyIncome = 'Monthly income cannot be negative.'
      }
    }

    if (stepNumber === 3) {
      expenses.forEach((expense) => {
        const amount = Number(expense.amount)
        if (!Number.isFinite(amount) || amount < 0) {
          errors[`expense-${expense.id}`] = 'Expense amount cannot be negative.'
        }
      })
    }

    if (stepNumber === 4) {
      const savings = Number(form.liquidSavings)
      const debt = Number(form.monthlyDebtPayments)

      if (!Number.isFinite(savings) || savings < 0) {
        errors.liquidSavings = 'Accessible savings cannot be negative.'
      }

      if (!Number.isFinite(debt) || debt < 0) {
        errors.monthlyDebtPayments = 'Monthly debt cannot be negative.'
      }

      if (!Number.isFinite(currentUrgency) || currentUrgency < 1 || currentUrgency > 10) {
        errors.urgency = 'Urgency must be between 1 and 10.'
      }
    }

    setFieldErrors((current) => ({...current, ...errors}))
    return Object.keys(errors).length === 0
  }

  function goNext() {
    setSubmitError('')
    if (!validateStep(step)) return

    const nextStep = Math.min(step + 1, steps.length)
    setStep(nextStep)
    setFurthestStep((current) => Math.max(current, nextStep))
  }

  function goBack() {
    setSubmitError('')
    setStep((current) => Math.max(current - 1, 1))
  }

  function navigateToStep(targetStep) {
    if (submitting || targetStep > furthestStep || targetStep === step) return
    if (targetStep > step && !validateStep(step)) return

    setSubmitError('')
    setStep(targetStep)
  }

  async function handleSubmit(event) {
    event.preventDefault()

    for (let stepNumber = 1; stepNumber <= steps.length; stepNumber += 1) {
      if (!validateStep(stepNumber)) {
        setStep(stepNumber)
        setSubmitError('Please check the highlighted field before continuing.')
        return
      }
    }

    const requestBody = {
      financialProfile: {
        incomeType: form.incomeType,
        monthlyIncome: numberOrZero(form.monthlyIncome),
        monthlyExpenses: expenses.map(({category, amount}) => ({
          category,
          amount: numberOrZero(amount),
        })),
        liquidSavings: numberOrZero(form.liquidSavings),
        monthlyDebtPayments: numberOrZero(form.monthlyDebtPayments),
      },
      purchase: {
        productName: form.productName.trim(),
        price: Number(form.price),
        currency: form.currency,
        alreadyOwnSimilarProduct: form.alreadyOwnSimilarProduct,
        usageFrequency: form.usageFrequency,
        urgency: currentUrgency,
      },
    }

    setSubmitting(true)
    setSubmitError('')
    setResponse(null)

    try {
      const result = await fetch('http://localhost:8080/api/analysis', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(requestBody),
      })

      if (!result.ok) throw new Error(`Backend returned HTTP ${result.status}`)
      setResponse(await result.json())
    } catch (requestError) {
      setSubmitError(
          requestError.message || 'We could not reach the BuyOrWait backend.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div>
          <div className="brand">
            <div className="brand-mark" aria-hidden="true">B</div>
            <div>
              <strong>BuyOrWait</strong>
              <span>Purchase decision assistant</span>
            </div>
          </div>

          <div className="sidebar-copy">
            <p className="sidebar-kicker">Think before you spend</p>
            <h2>A clearer view before you commit.</h2>
            <p>
              Put the purchase in context with a quick picture of your monthly
              finances and how useful it will be.
            </p>
          </div>

          <nav className="step-navigation" aria-label="Questionnaire steps">
            {steps.map((item) => {
              const active = item.number === step
              const completed = item.number < furthestStep && !active
              const unavailable = item.number > furthestStep

              return (
                <button
                  key={item.number}
                  type="button"
                  className={`step-item${active ? ' active' : ''}${completed ? ' completed' : ''}`}
                  onClick={() => navigateToStep(item.number)}
                  disabled={unavailable || submitting}
                  aria-current={active ? 'step' : undefined}
                >
                  <span className="step-number" aria-hidden="true">
                    {completed ? '✓' : item.number}
                  </span>
                  <span className="step-item-copy">
                    <strong>{item.title}</strong>
                    <span>{item.description}</span>
                  </span>
                </button>
              )
            })}
          </nav>
        </div>

        <div className="sidebar-footer">
          <span className="privacy-dot" aria-hidden="true" />
          No account required for this version.
        </div>
      </aside>

      <section className="workspace">
        <header className="mobile-header">
          <div>
            <strong>BuyOrWait</strong>
            <span>{steps[step - 1].title}</span>
          </div>
          <span>Step {step} of {steps.length}</span>
        </header>

        <div
          className="mobile-progress"
          role="progressbar"
          aria-label="Questionnaire progress"
          aria-valuemin="1"
          aria-valuemax={steps.length}
          aria-valuenow={step}
        >
          <div
            className="mobile-progress-fill"
            style={{width: `${(step / steps.length) * 100}%`}}
          />
        </div>

        <form className="questionnaire" onSubmit={handleSubmit} noValidate>
          {step === 1 && (
            <section className="step-panel" key="purchase" aria-labelledby="purchase-title">
              <div className="step-header">
                <p className="section-label">01 · The purchase</p>
                <h1 id="purchase-title">What are you thinking of buying?</h1>
                <p>Start with the basics. We’ll consider the price alongside your monthly finances.</p>
              </div>

              <div className="form-content">
                <label className="field" htmlFor="productName">
                  <span>Product name</span>
                  <input
                    id="productName"
                    name="productName"
                    value={form.productName}
                    onChange={updateForm}
                    placeholder="e.g. Sony WH-1000XM6"
                    className={fieldErrors.productName ? 'invalid' : ''}
                    aria-invalid={Boolean(fieldErrors.productName)}
                    aria-describedby={fieldErrors.productName ? 'productName-error' : undefined}
                    autoComplete="off"
                    autoFocus
                  />
                  <FieldError id="productName-error">{fieldErrors.productName}</FieldError>
                </label>

                <div className="price-row">
                  <label className="field" htmlFor="price">
                    <span>Price</span>
                    <div className={`money-input${fieldErrors.price ? ' invalid' : ''}`}>
                      <span className="currency-prefix" aria-hidden="true">{currencySymbol}</span>
                      <input
                        id="price"
                        name="price"
                        type="number"
                        inputMode="decimal"
                        min="0.01"
                        step="0.01"
                        value={form.price}
                        onChange={updateForm}
                        placeholder="34,990"
                        aria-invalid={Boolean(fieldErrors.price)}
                        aria-describedby={fieldErrors.price ? 'price-error' : undefined}
                      />
                    </div>
                    <FieldError id="price-error">{fieldErrors.price}</FieldError>
                  </label>

                  <label className="field" htmlFor="currency">
                    <span>Currency</span>
                    <select id="currency" name="currency" value={form.currency} onChange={updateForm}>
                      {Object.keys(currencies).map((currency) => (
                        <option key={currency} value={currency}>{currency}</option>
                      ))}
                    </select>
                  </label>
                </div>

                <fieldset className="field-group choice-fieldset">
                  <legend className="field-title">Do you already own something that serves the same purpose?</legend>
                  <div className="option-row two-options">
                    <button
                      type="button"
                      className={`option${!form.alreadyOwnSimilarProduct ? ' active' : ''}`}
                      aria-pressed={!form.alreadyOwnSimilarProduct}
                      onClick={() => setChoice('alreadyOwnSimilarProduct', false)}
                    >
                      <strong>No</strong>
                      <span>This would fill a new need</span>
                    </button>
                    <button
                      type="button"
                      className={`option${form.alreadyOwnSimilarProduct ? ' active' : ''}`}
                      aria-pressed={form.alreadyOwnSimilarProduct}
                      onClick={() => setChoice('alreadyOwnSimilarProduct', true)}
                    >
                      <strong>Yes</strong>
                      <span>I already have an alternative</span>
                    </button>
                  </div>
                </fieldset>
              </div>
            </section>
          )}

          {step === 2 && (
            <section className="step-panel" key="income" aria-labelledby="income-title">
              <div className="step-header">
                <p className="section-label">02 · Income</p>
                <h1 id="income-title">How does money usually come in?</h1>
                <p>A simple monthly picture is enough for this first analysis.</p>
              </div>

              <div className="form-content">
                <fieldset className="field-group choice-fieldset">
                  <legend className="field-title">Primary income type</legend>
                  <div className="choice-pills income-options">
                    {incomeTypes.map((type) => (
                      <button
                        key={type}
                        type="button"
                        className={`compact-option${form.incomeType === type ? ' active' : ''}`}
                        aria-pressed={form.incomeType === type}
                        onClick={() => setChoice('incomeType', type)}
                      >
                        {displayLabel(type)}
                      </button>
                    ))}
                  </div>
                </fieldset>

                <label className="field" htmlFor="monthlyIncome">
                  <span>Usual monthly take-home income</span>
                  <div className={`money-input${fieldErrors.monthlyIncome ? ' invalid' : ''}`}>
                    <span className="currency-prefix" aria-hidden="true">{currencySymbol}</span>
                    <input
                      id="monthlyIncome"
                      name="monthlyIncome"
                      type="number"
                      inputMode="decimal"
                      min="0"
                      step="0.01"
                      value={form.monthlyIncome}
                      onChange={updateForm}
                      placeholder="85,000"
                      aria-invalid={Boolean(fieldErrors.monthlyIncome)}
                      aria-describedby={fieldErrors.monthlyIncome
                        ? 'monthlyIncome-help monthlyIncome-error'
                        : 'monthlyIncome-help'}
                    />
                  </div>
                  <small id="monthlyIncome-help">An approximate monthly amount is fine.</small>
                  <FieldError id="monthlyIncome-error">{fieldErrors.monthlyIncome}</FieldError>
                </label>
              </div>
            </section>
          )}

          {step === 3 && (
            <section className="step-panel" key="expenses" aria-labelledby="expenses-title">
              <div className="step-header compact-header">
                <p className="section-label">03 · Expenses</p>
                <h1 id="expenses-title">What does a normal month cost?</h1>
                <p>Add the recurring expenses that matter to your budget. You can leave this empty for now.</p>
              </div>

              <div className="form-content expenses-content">
                <div className="expense-summary">
                  <div>
                    <span>Total monthly expenses</span>
                    <strong>{currencySymbol}{totalExpenses.toLocaleString(undefined, {maximumFractionDigits: 2})}</strong>
                  </div>
                  {expenses.length > 0 && !allCategoriesUsed && (
                    <button type="button" className="add-expense" onClick={addExpense}>
                      <span aria-hidden="true">+</span> Add expense
                    </button>
                  )}
                </div>

                {expenses.length === 0 ? (
                  <button type="button" className="empty-expenses" onClick={addExpense}>
                    <span className="empty-plus" aria-hidden="true">+</span>
                    <strong>Add a monthly expense</strong>
                    <span>Start with housing, groceries, transport, or another regular cost.</span>
                  </button>
                ) : (
                  <div className="expense-list">
                    {expenses.map((expense, index) => (
                      <div className="expense-item" key={expense.id}>
                        <div className="expense-row">
                          <label className="visually-hidden" htmlFor={`expense-category-${expense.id}`}>
                            Expense {index + 1} category
                          </label>
                          <select
                            id={`expense-category-${expense.id}`}
                            value={expense.category}
                            onChange={(event) => updateExpense(expense.id, 'category', event.target.value)}
                          >
                            {categoriesFor(expense).map((category) => (
                              <option key={category} value={category}>{displayLabel(category)}</option>
                            ))}
                          </select>

                          <label className="visually-hidden" htmlFor={`expense-amount-${expense.id}`}>
                            {displayLabel(expense.category)} monthly amount
                          </label>
                          <div className={`expense-money${fieldErrors[`expense-${expense.id}`] ? ' invalid' : ''}`}>
                            <span aria-hidden="true">{currencySymbol}</span>
                            <input
                              id={`expense-amount-${expense.id}`}
                              type="number"
                              inputMode="decimal"
                              min="0"
                              step="0.01"
                              value={expense.amount}
                              placeholder="0"
                              onChange={(event) => updateExpense(expense.id, 'amount', event.target.value)}
                              aria-invalid={Boolean(fieldErrors[`expense-${expense.id}`])}
                              aria-describedby={`expense-error-${expense.id}`}
                            />
                          </div>

                          <button
                            type="button"
                            className="remove-expense"
                            aria-label={`Remove ${displayLabel(expense.category)} expense`}
                            onClick={() => removeExpense(expense.id)}
                          >
                            <span aria-hidden="true">×</span>
                          </button>
                        </div>
                        <FieldError id={`expense-error-${expense.id}`}>
                          {fieldErrors[`expense-${expense.id}`]}
                        </FieldError>
                      </div>
                    ))}
                  </div>
                )}

                {allCategoriesUsed && (
                  <p className="supporting-text">All expense categories have been added.</p>
                )}
              </div>
            </section>
          )}

          {step === 4 && (
            <section className="step-panel" key="context" aria-labelledby="context-title">
              <div className="step-header compact-header">
                <p className="section-label">04 · Final context</p>
                <h1 id="context-title">One last check.</h1>
                <p>Add your financial buffer, then tell us how useful and urgent this purchase is.</p>
              </div>

              <div className="form-content final-context">
                <div className="two-column-fields">
                  <label className="field" htmlFor="liquidSavings">
                    <span>Accessible savings</span>
                    <div className={`money-input${fieldErrors.liquidSavings ? ' invalid' : ''}`}>
                      <span className="currency-prefix" aria-hidden="true">{currencySymbol}</span>
                      <input
                        id="liquidSavings"
                        name="liquidSavings"
                        type="number"
                        inputMode="decimal"
                        min="0"
                        step="0.01"
                        value={form.liquidSavings}
                        onChange={updateForm}
                        placeholder="300,000"
                        aria-invalid={Boolean(fieldErrors.liquidSavings)}
                        aria-describedby={fieldErrors.liquidSavings
                          ? 'liquidSavings-help liquidSavings-error'
                          : 'liquidSavings-help'}
                      />
                    </div>
                    <small id="liquidSavings-help">Money you could access if needed.</small>
                    <FieldError id="liquidSavings-error">{fieldErrors.liquidSavings}</FieldError>
                  </label>

                  <label className="field" htmlFor="monthlyDebtPayments">
                    <span>Monthly EMI / debt</span>
                    <div className={`money-input${fieldErrors.monthlyDebtPayments ? ' invalid' : ''}`}>
                      <span className="currency-prefix" aria-hidden="true">{currencySymbol}</span>
                      <input
                        id="monthlyDebtPayments"
                        name="monthlyDebtPayments"
                        type="number"
                        inputMode="decimal"
                        min="0"
                        step="0.01"
                        value={form.monthlyDebtPayments}
                        onChange={updateForm}
                        placeholder="0"
                        aria-invalid={Boolean(fieldErrors.monthlyDebtPayments)}
                        aria-describedby={fieldErrors.monthlyDebtPayments
                          ? 'monthlyDebtPayments-error'
                          : undefined}
                      />
                    </div>
                    <FieldError id="monthlyDebtPayments-error">{fieldErrors.monthlyDebtPayments}</FieldError>
                  </label>
                </div>

                <fieldset className="field-group choice-fieldset">
                  <legend className="field-title">How often will you realistically use it?</legend>
                  <div className="choice-pills usage-options">
                    {usageFrequencies.map((frequency) => (
                      <button
                        key={frequency}
                        type="button"
                        className={`compact-option${form.usageFrequency === frequency ? ' active' : ''}`}
                        aria-pressed={form.usageFrequency === frequency}
                        onClick={() => setChoice('usageFrequency', frequency)}
                      >
                        {displayLabel(frequency)}
                      </button>
                    ))}
                  </div>
                </fieldset>

                <div className="field-group urgency-group">
                  <div className="urgency-heading">
                    <div>
                      <label className="field-title" htmlFor="urgency">How urgent is the purchase?</label>
                      <span className="urgency-label">{urgencyLabel(currentUrgency)}</span>
                    </div>
                    <output htmlFor="urgency" aria-live="polite">{currentUrgency}<small>/10</small></output>
                  </div>
                  <input
                    id="urgency"
                    className="urgency-slider"
                    name="urgency"
                    type="range"
                    min="1"
                    max="10"
                    value={form.urgency}
                    onChange={updateForm}
                    style={{'--range-progress': `${((currentUrgency - 1) / 9) * 100}%`}}
                    aria-describedby={fieldErrors.urgency
                      ? 'urgency-scale urgency-error'
                      : 'urgency-scale'}
                  />
                  <div className="range-labels" id="urgency-scale">
                    <span>1 · Easy to wait</span>
                    <span>10 · Need it soon</span>
                  </div>
                  <FieldError id="urgency-error">{fieldErrors.urgency}</FieldError>
                </div>
              </div>
            </section>
          )}

          <div className="form-footer">
            <div className="feedback-area" aria-live="polite">
              {submitError && <div className="feedback error-feedback" role="alert">{submitError}</div>}
              {response && (
                <div
                  className={`feedback analysis-result verdict-${response.verdict.toLowerCase()}`}
                  role="status"
                >
                  <div className="result-heading">
                    <strong>{response.verdict}</strong>
                    <span>{response.confidence}% confidence</span>
                  </div>
                  <p>{response.summary}</p>
                  <div className="result-reasons">
                    <span>Why</span>
                    <ul>
                      {response.reasons.map((reason) => (
                        <li key={reason}>{reason}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              )}
            </div>

            <div className="action-bar">
              <button
                type="button"
                className={`back-button${step === 1 ? ' hidden' : ''}`}
                onClick={goBack}
                disabled={step === 1 || submitting}
                tabIndex={step === 1 ? -1 : 0}
              >
                <span aria-hidden="true">←</span> Back
              </button>

              {step < steps.length ? (
                <button type="button" className="continue-button" onClick={goNext}>
                  Continue <span aria-hidden="true">→</span>
                </button>
              ) : (
                <button type="submit" className="continue-button analyse-button" disabled={submitting}>
                  {submitting && <span className="spinner" aria-hidden="true" />}
                  {submitting ? 'Analysing purchase…' : 'Analyse purchase'}
                  {!submitting && <span aria-hidden="true">→</span>}
                </button>
              )}
            </div>
          </div>
        </form>
      </section>
    </main>
  )
}
