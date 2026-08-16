import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ResumeUploadCard } from '../pages/resume/ResumeUploadCard'

describe('ResumeUploadCard', () => {
  it('rejects non-PDF files without calling onUpload', async () => {
    const onUpload = vi.fn()
    render(<ResumeUploadCard onUpload={onUpload} />)

    // userEvent.upload() respects the input's `accept` filter and won't fire
    // a change event for a mismatched file type, so we dispatch the DOM
    // event directly here to exercise the component's own validation logic.
    const input = screen.getByLabelText(/choose a pdf file/i, { selector: 'input' }) as HTMLInputElement
    const textFile = new File(['hello'], 'resume.txt', { type: 'text/plain' })
    fireEvent.change(input, { target: { files: [textFile] } })

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/please upload a pdf/i))
    expect(onUpload).not.toHaveBeenCalled()
  })

  it('calls onUpload with a valid PDF file', async () => {
    const onUpload = vi.fn().mockResolvedValue(undefined)
    render(<ResumeUploadCard onUpload={onUpload} />)
    const user = userEvent.setup()

    const input = screen.getByLabelText(/choose a pdf file/i, { selector: 'input' })
    const pdfFile = new File(['%PDF-1.4'], 'resume.pdf', { type: 'application/pdf' })
    await user.upload(input, pdfFile)

    await waitFor(() => expect(onUpload).toHaveBeenCalledWith(pdfFile))
  })
})
